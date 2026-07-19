package com.acme.json.helper.core.archive;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 压缩包索引缓存（项目级服务）
 * <p>
 * 按压缩包路径缓存条目索引，以修改时间与体积校验失效；
 * 索引构建失败/超限时缓存空标记，避免重复尝试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
@Service(Service.Level.PROJECT)
public final class ArchiveCacheService implements Disposable {
    /**
     * 日志
     */
    private static final Logger LOG = Logger.getInstance(ArchiveCacheService.class);
    /**
     * 索引缓存（路径 → 缓存项）
     */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    /**
     * 项目压缩包清单缓存
     */
    private volatile List<String> projectArchives;
    /**
     * 项目压缩包清单对应的 VFS 结构修改计数
     */
    private volatile long projectArchivesStamp = -1L;

    public static ArchiveCacheService getInstance(@NotNull final Project project) {
        return project.getService(ArchiveCacheService.class);
    }

    /**
     * 缓存项（条目索引 + 内容索引 + 失效校验信息；null 表示对应索引不可用）
     *
     * @param index         条目索引（null 表示该包不可索引或构建失败）
     * @param contentIndex  内容搜索索引（null 表示未构建或构建失败）
     * @param contentLoaded 内容索引是否已尝试构建（区分"未构建"与"构建失败"）
     * @param lastModified  压缩包最后修改时间
     * @param length        压缩包体积
     */
    private record CacheEntry(@Nullable ArchiveIndex index, @Nullable ArchiveContentIndex contentIndex,
                              boolean contentLoaded, long lastModified, long length) {
    }

    /**
     * 获取压缩包索引（懒构建；不可索引时返回 null 且缓存该结论）
     *
     * @param archivePath 压缩包绝对路径
     * @return 条目索引；不可索引/构建失败返回 null
     */
    public @Nullable ArchiveIndex getIndex(final String archivePath) {
        final CacheEntry snapshot = this.loadEntry(archivePath, false);
        return Objects.nonNull(snapshot) ? snapshot.index() : null;
    }

    /**
     * 获取压缩包内容搜索索引（懒构建；不可索引时返回 null 且缓存该结论）
     *
     * @param archivePath 压缩包绝对路径
     * @return 内容索引；不可索引/构建失败返回 null
     */
    public @Nullable ArchiveContentIndex getContentIndex(final String archivePath) {
        final CacheEntry snapshot = this.loadEntry(archivePath, true);
        return Objects.nonNull(snapshot) ? snapshot.contentIndex() : null;
    }

    /**
     * 加载缓存项（按需构建条目索引与内容索引，mtime/size 校验失效）
     *
     * @param archivePath  压缩包绝对路径
     * @param needContent  是否同时保证内容索引可用
     * @return 缓存项；压缩包不存在或格式不支持返回 null
     */
    private @Nullable CacheEntry loadEntry(final String archivePath, final boolean needContent) {
        if (StrUtil.isEmpty(archivePath)) {
            return null;
        }
        final File file = new File(archivePath);
        if (!file.isFile()) {
            return null;
        }
        final ArchiveFormats format = ArchiveFormats.of(file.getName());
        if (Objects.isNull(format) || !ArchiveIndex.isIndexable(file, format)) {
            return null;
        }
        final long lastModified = file.lastModified();
        final long length = file.length();
        final CacheEntry snapshot = this.cache.get(archivePath);
        if (Objects.nonNull(snapshot) && snapshot.lastModified() == lastModified && snapshot.length() == length
                && (!needContent || snapshot.contentLoaded())) {
            return snapshot;
        }
        synchronized (this.cache) {
            final CacheEntry recheck = this.cache.get(archivePath);
            if (Objects.nonNull(recheck) && recheck.lastModified() == lastModified && recheck.length() == length
                    && (!needContent || recheck.contentLoaded())) {
                return recheck;
            }
            ArchiveIndex index = Objects.nonNull(recheck) ? recheck.index() : null;
            if (Objects.isNull(recheck)) {
                try {
                    index = ArchiveIndex.build(file, format);
                } catch (final Exception e) {
                    LOG.debug("压缩包索引构建失败: %s - %s".formatted(archivePath, e.getMessage()));
                }
            }
            ArchiveContentIndex contentIndex = null;
            if (needContent && Objects.nonNull(index)) {
                try {
                    contentIndex = ArchiveContentIndex.build(file, format, index);
                } catch (final Exception e) {
                    LOG.debug("压缩包内容索引构建失败: %s - %s".formatted(archivePath, e.getMessage()));
                }
            }
            final CacheEntry entry = new CacheEntry(index, contentIndex, needContent, lastModified, length);
            this.cache.put(archivePath, entry);
            return entry;
        }
    }

    /**
     * 使指定压缩包的索引失效（压缩包内容变更后调用）
     *
     * @param archivePath 压缩包绝对路径
     */
    public void invalidate(final String archivePath) {
        this.cache.remove(archivePath);
    }

    /**
     * 获取项目内全部支持的压缩包路径（随 VFS 结构变更自动重建）
     *
     * @param project 项目
     * @return 压缩包路径列表
     */
    public @NotNull List<String> getProjectArchives(@NotNull final Project project) {
        final long currentStamp = VirtualFileManager.getInstance().getStructureModificationCount();
        final List<String> snapshot = this.projectArchives;
        if (Objects.nonNull(snapshot) && this.projectArchivesStamp == currentStamp) {
            return snapshot;
        }
        synchronized (this.cache) {
            if (Objects.nonNull(this.projectArchives) && this.projectArchivesStamp == currentStamp) {
                return this.projectArchives;
            }
            final List<String> archives = new ArrayList<>();
            ReadAction.computeBlocking(() -> {
                ProjectFileIndex.getInstance(project).iterateContent(file -> {
                    if (!file.isDirectory() && Objects.nonNull(ArchiveFormats.of(file.getName()))) {
                        archives.add(file.getPath());
                    }
                    return Boolean.TRUE;
                });
                return null;
            });
            this.projectArchives = List.copyOf(archives);
            this.projectArchivesStamp = currentStamp;
            return this.projectArchives;
        }
    }

    @Override
    public void dispose() {
        this.cache.clear();
        this.projectArchives = null;
        this.projectArchivesStamp = -1L;
    }
}
