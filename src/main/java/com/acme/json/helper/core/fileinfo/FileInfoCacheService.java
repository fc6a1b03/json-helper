package com.acme.json.helper.core.fileinfo;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件信息缓存（项目级服务）
 * <p>
 * 为项目树节点装饰器提供"文件名右侧注释与修改时间"的展示文本：
 * 懒加载——仅当节点实际渲染（父目录展开）时才触发后台加载，绝不预扫全项目；
 * 缓存以 VFS 时间戳校验失效（编辑器保存即更新，外部修改经 VFS 刷新后自愈），无需额外文件监听；
 * 加载任务按文件 single-flight 去重，项目树刷新经防抖队列合并，避免刷新风暴
 *
 * @author 拒绝者
 * @date 2026-07-21
 */
@Service(Service.Level.PROJECT)
public final class FileInfoCacheService implements Disposable {
    /**
     * 日志
     */
    private static final Logger LOG = Logger.getInstance(FileInfoCacheService.class);
    /**
     * 缓存容量上限（超限整体清空重建，避免项目巨大时无界增长）
     */
    private static final int CACHE_LIMIT = 10_000;
    /**
     * 项目树刷新防抖间隔（毫秒）：批量加载完成仅产生一次刷新
     */
    private static final int REFRESH_DEBOUNCE_MS = 150;
    /**
     * 刷新任务合并标识（同一标识的刷新请求被防抖队列合并）
     */
    private static final Object REFRESH_IDENTITY = new Object();
    /**
     * 所属项目
     */
    private final Project project;
    /**
     * 展示文本缓存（文件路径 → 缓存项）
     */
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    /**
     * 进行中的加载任务（文件路径 → 加载 Future，single-flight 并发去重）
     */
    private final Map<String, CompletableFuture<Void>> loading = new ConcurrentHashMap<>();
    /**
     * 项目树刷新防抖队列（与项目面板一致的合并刷新惯例）
     */
    private final MergingUpdateQueue refreshQueue;

    /**
     * 缓存项
     *
     * @param suffix    文件名右侧展示文本（必含修改时间；注释缺失时仅时间）
     * @param timeStamp 加载时的 VFS 时间戳（失效校验依据）
     */
    private record Entry(@NotNull String suffix, long timeStamp) {
    }

    public FileInfoCacheService(final Project project) {
        this.project = project;
        this.refreshQueue = new MergingUpdateQueue(
                "JsonHelper.FileInfoRefresh", REFRESH_DEBOUNCE_MS, Boolean.TRUE, null, this, null, Alarm.ThreadToUse.POOLED_THREAD
        );
    }

    public static FileInfoCacheService getInstance(@NotNull final Project project) {
        return project.getService(FileInfoCacheService.class);
    }

    /**
     * 获取文件名右侧展示文本（纯内存查询，EDT 渲染安全）
     * <p>
     * 缓存未命中或文件已修改时触发后台懒加载并返回 null（本次不展示）；
     * 加载完成合并刷新项目树后自然展示，渲染线程零等待
     *
     * @param file 节点文件
     * @return 展示文本；未就绪返回 null
     */
    public @Nullable String suffixOf(@NotNull final VirtualFile file) {
        final String path = file.getPath();
        final long stamp = file.getTimeStamp();
        final Entry hit = this.cache.get(path);
        if (hit != null && hit.timeStamp() == stamp) {
            return hit.suffix();
        }
        if (hit != null) {
            // 文件已修改：清旧缓存后重载
            this.cache.remove(path);
        }
        this.schedule(file);
        return null;
    }

    /**
     * 后台懒加载（同一路径 single-flight 去重）
     *
     * @param file 节点文件
     */
    private void schedule(@NotNull final VirtualFile file) {
        this.loading.computeIfAbsent(file.getPath(), path -> CompletableFuture.runAsync(() -> {
            try {
                final long stamp = file.getTimeStamp();
                final String suffix = FileInfoDisplay.format(readComment(file), stamp);
                if (this.cache.size() >= CACHE_LIMIT) {
                    this.cache.clear();
                }
                this.cache.put(path, new Entry(suffix, stamp));
                this.refreshQueue.queue(Update.create(REFRESH_IDENTITY, this::refreshProjectView));
            } catch (final Exception e) {
                LOG.debug("文件头注释读取失败: %s - %s".formatted(path, e.getMessage()));
            } finally {
                this.loading.remove(path);
            }
        }, AppExecutorUtil.getAppExecutorService()));
    }

    /**
     * 刷新项目树（防抖队列在后台线程触发，切 EDT 执行）
     */
    private void refreshProjectView() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!this.project.isDisposed()) {
                ProjectView.getInstance(this.project).refresh();
            }
        }, this.project.getDisposed());
    }

    /**
     * 读取文件头并提取注释摘要
     *
     * @param file 节点文件
     * @return 注释摘要；无注释返回 null
     * @throws IOException 读取失败
     */
    private static @Nullable String readComment(@NotNull final VirtualFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return FileCommentExtractor.extract(new String(in.readNBytes(FileCommentExtractor.HEAD_BYTES), file.getCharset()));
        }
    }

    @Override
    public void dispose() {
        this.cache.clear();
        this.loading.clear();
    }
}
