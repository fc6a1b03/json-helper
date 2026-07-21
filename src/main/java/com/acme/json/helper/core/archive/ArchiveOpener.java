package com.acme.json.helper.core.archive;

import com.acme.json.helper.core.notice.Notifier;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * 压缩包条目打开器
 * <p>
 * zip 系（zip/jar/war/ear）直接经由平台 JarFileSystem 打开包内真实 VFS 文件，
 * 零字节拷贝、零临时文件，获得与平台一致的完整能力（.class 反编译、语法高亮、编码检测）；
 * 其余格式（7z/tar 系/单文件压缩）将条目流式解压为临时真实文件后经 LocalFileSystem 打开。
 * 条目内容全程不经过内存字符串中转，杜绝二进制条目（如 .class）在字符集往返中被破坏
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
public final class ArchiveOpener {
    /**
     * 日志
     */
    private static final Logger LOG = Logger.getInstance(ArchiveOpener.class);
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 临时解压根目录（系统临时目录下，按压缩包路径散列隔离，内容未变化时复用）
     */
    private static final File TEMP_ROOT = new File(FileUtil.getTempDirectory(), "json-helper-archive");

    private ArchiveOpener() {
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
        CompletableFuture.supplyAsync(() -> locateEntryFile(archivePath, node), AppExecutorUtil.getAppExecutorService())
                .thenAccept(virtualFile -> ApplicationManager.getApplication().invokeLater(() -> {
                    if (Objects.isNull(virtualFile)) {
                        Notifier.notifyError(BUNDLE.getString("archive.entry.open.failed"), project);
                        return;
                    }
                    if (line > 0) {
                        // 内容命中时打开编辑器并显式定位光标到命中行（滚动居中）
                        final var editor = FileEditorManager.getInstance(project).openTextEditor(
                                new OpenFileDescriptor(project, virtualFile, line - 1, 0), Boolean.TRUE);
                        if (Objects.nonNull(editor)) {
                            final int targetLine = Math.max(0, Math.min(line - 1, editor.getDocument().getLineCount() - 1));
                            editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(targetLine, 0));
                            editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
                        }
                    } else {
                        FileEditorManager.getInstance(project).openFile(virtualFile, Boolean.TRUE);
                    }
                    // 项目树同步定位到该条目（展开压缩包并选中条目节点；不抢编辑器焦点）
                    selectInProjectTree(project, archivePath, node.path());
                }));
    }

    /**
     * 在项目树中定位并选中压缩包条目节点
     * <p>
     * 平台 Select Opened File 的遍历模型不会进入文件节点下注入的子树（文件节点 contains 恒 false），
     * 因此自行遍历：仅沿"目标压缩包所在目录链 + 条目祖先链"下钻（成本与树深度成正比），
     * 命中条目节点后选中并滚动可见
     *
     * @param project     项目
     * @param archivePath 压缩包绝对路径
     * @param entryPath   条目路径
     */
    private static void selectInProjectTree(final Project project, final String archivePath, final String entryPath) {
        final VirtualFile archiveFile = LocalFileSystem.getInstance().findFileByIoFile(new File(archivePath));
        final AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
        final JTree tree = Objects.isNull(pane) ? null : pane.getTree();
        if (Objects.isNull(archiveFile) || Objects.isNull(tree)) {
            return;
        }
        TreeUtil.promiseVisit(tree, path -> {
            final Object userObject = TreeUtil.getUserObject(path.getLastPathComponent());
            if (userObject instanceof final ArchiveEntryNode entryNode) {
                if (entryNode.isSameEntry(archivePath, entryPath)) {
                    return TreeVisitor.Action.INTERRUPT;
                }
                return entryNode.isAncestorEntry(archivePath, entryPath)
                        ? TreeVisitor.Action.CONTINUE : TreeVisitor.Action.SKIP_CHILDREN;
            }
            if (userObject instanceof final ProjectViewNode<?> viewNode) {
                final VirtualFile file = viewNode.getVirtualFile();
                if (Objects.nonNull(file) && !file.equals(archiveFile) && !VfsUtilCore.isAncestor(file, archiveFile, false)) {
                    // 与目标压缩包无关的分支不进入
                    return TreeVisitor.Action.SKIP_CHILDREN;
                }
            }
            return TreeVisitor.Action.CONTINUE;
        }).onSuccess(path -> {
            if (Objects.nonNull(path)) {
                tree.getSelectionModel().setSelectionPath(path);
                tree.scrollPathToVisible(path);
            }
        });
    }

    /**
     * 后台线程定位条目对应的真实 VFS 文件（失败返回 null）
     *
     * @param archivePath 压缩包绝对路径
     * @param node        条目节点
     * @return 条目对应的 VFS 文件
     */
    @Nullable
    private static VirtualFile locateEntryFile(final String archivePath, final ArchiveIndex.Node node) {
        final File archiveFile = new File(archivePath);
        final ArchiveFormats format = ArchiveFormats.of(archiveFile.getName());
        if (Objects.isNull(format)) {
            return null;
        }
        try {
            final VirtualFile virtualFile = switch (format) {
                case ZIP -> jarEntryFile(archiveFile, node);
                default -> tempEntryFile(archiveFile, format, node);
            };
            if (Objects.nonNull(virtualFile)) {
                // 后台线程预载文本（.class 触发反编译进入平台缓存，避免 EDT 首次反编译冻结 UI）
                LoadTextUtil.loadText(virtualFile);
            }
            return virtualFile;
        } catch (final Exception e) {
            LOG.debug("压缩包条目打开失败: %s!%s - %s".formatted(archivePath, node.path(), e.getMessage()));
            return null;
        }
    }

    /**
     * zip 系条目：平台 JarFileSystem 直接提供包内真实文件（零拷贝，平台托管句柄缓存与反编译缓存）
     *
     * @param archiveFile 压缩包文件
     * @param node        条目节点
     * @return 条目对应的 VFS 文件；条目不存在返回 null
     */
    @Nullable
    private static VirtualFile jarEntryFile(final File archiveFile, final ArchiveIndex.Node node) {
        return JarFileSystem.getInstance().refreshAndFindFileByPath(ArchiveFormats.zipEntryVfsPath(archiveFile, node.path()));
    }

    /**
     * 其他格式条目：流式解压为临时真实文件（按压缩包与条目路径散列命名，内容未变化则直接复用）
     *
     * @param archiveFile 压缩包文件
     * @param format      压缩包格式
     * @param node        条目节点
     * @return 条目对应的 VFS 文件；读取失败返回 null
     * @throws IOException 解压或写盘失败
     */
    @Nullable
    private static VirtualFile tempEntryFile(final File archiveFile, final ArchiveFormats format, final ArchiveIndex.Node node) throws IOException {
        final byte[] content = format.readEntryContent(archiveFile, node.path(), ArchiveIndex.MAX_ENTRY_OPEN_SIZE);
        if (Objects.isNull(content)) {
            return null;
        }
        final File dir = new File(TEMP_ROOT, Integer.toHexString(archiveFile.getAbsolutePath().hashCode()));
        final File tempFile = new File(dir, Integer.toHexString(node.path().hashCode()) + "-" + node.name());
        if (!tempFile.isFile() || tempFile.length() != content.length || tempFile.lastModified() < archiveFile.lastModified()) {
            Files.createDirectories(dir.toPath());
            // 覆盖前解除只读，避免上次打开的只读标记导致写入失败
            tempFile.setWritable(Boolean.TRUE);
            Files.write(tempFile.toPath(), content);
            // 标记只读：编辑器内不可误改（不回写压缩包）
            tempFile.setReadOnly();
        }
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
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
        } catch (final Exception e) {
            LOG.debug("压缩包条目文本提取失败: %s!%s - %s".formatted(archivePath, entryPath, e.getMessage()));
            return "";
        }
    }
}
