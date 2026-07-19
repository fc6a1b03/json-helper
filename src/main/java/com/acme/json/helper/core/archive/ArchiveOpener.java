package com.acme.json.helper.core.archive;

import com.acme.json.helper.core.notice.Notifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * 压缩包条目打开器
 * <p>
 * 后台线程读取条目内容后，以只读虚拟文件打开到编辑器；
 * 与平台对 jar 内文件的行为一致：可浏览、不可编辑（不回写压缩包）
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
public final class ArchiveOpener {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 虚拟文件缓存容量上限（LRU 策略，超出后淘汰最久未访问项，防止无界增长）
     */
    private static final int VIRTUAL_FILE_CACHE_CAPACITY = 100;
    /**
     * 虚拟文件实例缓存（条目路径 → 实例；复用实例使命中平台反编译文本缓存，避免重复反编译）
     */
    private static final Map<String, ReadOnlyEntryFile> VIRTUAL_FILE_CACHE = new LinkedHashMap<>(VIRTUAL_FILE_CACHE_CAPACITY, 0.75f, Boolean.TRUE) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, ReadOnlyEntryFile> eldest) {
            return this.size() > VIRTUAL_FILE_CACHE_CAPACITY;
        }
    };

    private ArchiveOpener() {
    }

    /**
     * 只读压缩包条目虚拟文件
     */
    private static final class ReadOnlyEntryFile extends LightVirtualFile {
        private ReadOnlyEntryFile(final String name, final FileType fileType, final byte[] content) {
            super(name, fileType, "");
            try {
                // 以二进制形式填充内容，文本与二进制条目均可安全打开
                this.setBinaryContent(content);
            } catch (final java.io.IOException ignored) {
                // 内存字节数组不会触发真实 IO 异常
            }
            this.setWritable(false);
        }
    }

    /**
     * 打开压缩包内的文件条目到编辑器
     *
     * @param project     项目
     * @param archivePath 压缩包绝对路径
     * @param node        条目节点
     */
    public static void open(@NotNull final Project project, final String archivePath, final ArchiveIndex.Node node) {
        open(project, archivePath, node, 0);
    }

    /**
     * 打开压缩包内的文件条目到编辑器
     *
     * @param project     项目
     * @param archivePath 压缩包绝对路径
     * @param node        条目节点
     * @param line        定位行号（1 起始；0 表示不定位）
     */
    public static void open(@NotNull final Project project, final String archivePath, final ArchiveIndex.Node node, final int line) {
        if (node.directory()) {
            return;
        }
        if (node.size() > ArchiveIndex.MAX_ENTRY_OPEN_SIZE) {
            Notifier.notifyWarn(BUNDLE.getString("archive.entry.too.large"), project);
            return;
        }
        final String cacheKey = archivePath + "!" + node.path();
        CompletableFuture.supplyAsync(() -> {
                    // 实例复用：同一条目二次打开直接命中（含平台反编译文本缓存）
                    final ReadOnlyEntryFile cached;
                    synchronized (VIRTUAL_FILE_CACHE) {
                        cached = VIRTUAL_FILE_CACHE.get(cacheKey);
                    }
                    if (Objects.nonNull(cached)) {
                        return cached;
                    }
                    final File archiveFile = new File(archivePath);
                    final ArchiveFormats format = ArchiveFormats.of(archiveFile.getName());
                    if (Objects.isNull(format)) {
                        return null;
                    }
                    try {
                        final byte[] content = format.readEntryContent(archiveFile, node.path(), ArchiveIndex.MAX_ENTRY_OPEN_SIZE);
                        if (Objects.isNull(content)) {
                            return null;
                        }
                        final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(node.name());
                        final ReadOnlyEntryFile virtualFile = new ReadOnlyEntryFile(node.name(), fileType, content);
                        // 在后台线程预加载文本（.class 等二进制会触发反编译，避免 EDT 首次反编译冻结 UI）
                        LoadTextUtil.loadText(virtualFile);
                        synchronized (VIRTUAL_FILE_CACHE) {
                            VIRTUAL_FILE_CACHE.put(cacheKey, virtualFile);
                        }
                        return virtualFile;
                    } catch (final Exception ignored) {
                        return null;
                    }
                }, AppExecutorUtil.getAppExecutorService())
                .thenAccept(virtualFile -> ApplicationManager.getApplication().invokeLater(() -> {
                    if (Objects.isNull(virtualFile)) {
                        Notifier.notifyError(BUNDLE.getString("archive.entry.open.failed"), project);
                        return;
                    }
                    if (line > 0) {
                        // 内容命中时直接定位到命中行
                        new OpenFileDescriptor(project, virtualFile, line - 1, 0).navigate(Boolean.TRUE);
                    } else {
                        FileEditorManager.getInstance(project).openFile(virtualFile, Boolean.TRUE);
                    }
                }));
    }

    /**
     * 提取条目内容的文本（供搜索匹配等场景使用）
     *
     * @param archivePath 压缩包绝对路径
     * @param entryPath   条目路径
     * @param maxSize     最大读取大小
     * @return 文本内容；失败返回空串
     */
    public static String readEntryText(final String archivePath, final String entryPath, final long maxSize) {
        final File archiveFile = new File(archivePath);
        final ArchiveFormats format = ArchiveFormats.of(archiveFile.getName());
        if (Objects.isNull(format)) {
            return "";
        }
        try {
            final byte[] content = format.readEntryContent(archiveFile, entryPath, maxSize);
            return Objects.nonNull(content) ? new String(content, StandardCharsets.UTF_8) : "";
        } catch (final Exception ignored) {
            return "";
        }
    }
}
