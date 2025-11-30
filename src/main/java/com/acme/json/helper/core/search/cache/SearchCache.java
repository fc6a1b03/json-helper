package com.acme.json.helper.core.search.cache;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.search.item.HttpRequestItem;
import com.acme.json.helper.core.search.item.ProjectNavigationItem;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 搜索缓存
 * @author 拒绝者
 * @date 2025-11-05
 */
@Service(Service.Level.PROJECT)
public final class SearchCache implements Supplier<SequencedCollection<ProjectNavigationItem>>, Disposable {
    /**
     * Git仓库缓存 (使用LRU策略，最多保留20个)
     */
    private final LinkedHashMap<String, Long> gitRepositoryCache = new LinkedHashMap<>(20, 0.75f, Boolean.TRUE) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Long> eldest) {
            return this.size() > 20;
        }
    };
    /**
     * HTTP请求文件缓存
     */
    private SequencedCollection<HttpRequestItem> httpCached;
    /**
     * 项目缓存
     */
    private SequencedCollection<ProjectNavigationItem> cached;

    /**
     * 从Git URL提取组织和仓库名
     * @param url Git URL
     * @return 组织/仓库对
     */
    private static String[] extractOrgAndRepo(final String url) {
        // 清理URL
        String cleanUrl = url
                .replaceFirst("^git@", "")
                .replaceFirst("^https?://", "")
                .replaceFirst("^ssh://", "")
                .replaceFirst("^git://", "")
                .replaceAll("\\.git$", "");
        // 处理主机名后的路径
        cleanUrl = cleanUrl.replaceFirst("^[^/]+:", "");
        cleanUrl = cleanUrl.replaceFirst("^[^/]+/", "");
        final String[] parts = cleanUrl.split("/");
        return parts.length >= 2 ? new String[]{parts[parts.length - 2], parts[parts.length - 1]} : new String[]{"unknown", "repo"};
    }

    /**
     * 获取缓存项目
     * @return {@link SequencedCollection}<{@link ProjectNavigationItem}>
     */
    @Override
    public SequencedCollection<ProjectNavigationItem> get() {
        return Objects.isNull(this.cached) ? this.cached = this.load() : this.cached;
    }

    /**
     * 加载缓存项目
     * @return {@link SequencedCollection}<{@link ProjectNavigationItem}>
     */
    public SequencedCollection<ProjectNavigationItem> load() {
        // 已打开项目
        final Stream<ProjectNavigationItem> opened = Arrays.stream(ProjectManager.getInstance().getOpenProjects())
                .map(project -> ProjectNavigationItem.opened(project.getName(), project.getPresentableUrl(), 0));
        // 最近项目（排除已打开）
        final Set<String> openedPaths = Arrays.stream(ProjectManager.getInstance().getOpenProjects())
                .map(Project::getPresentableUrl).filter(StrUtil::isNotEmpty).collect(Collectors.toSet());
        final Stream<ProjectNavigationItem> recent = ((RecentProjectsManagerBase) RecentProjectsManager.getInstance())
                .getRecentPaths().stream().filter(Objects::nonNull).filter(p -> !openedPaths.contains(p))
                .map(path -> ProjectNavigationItem.recent(((RecentProjectsManagerBase) RecentProjectsManager.getInstance()).getProjectName(path), path, 0));
        // Git仓库
        final Stream<ProjectNavigationItem> gitRepositories = this.gitRepositoryCache.keySet().stream().filter(Objects::nonNull).map(this::createGitRepositoryItem);
        // 合并去重并保持顺序
        return Stream.of(opened, recent, gitRepositories).flatMap(Function.identity()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 获取HTTP请求文件缓存
     * @return HTTP请求文件列表
     */
    public SequencedCollection<HttpRequestItem> getHttp() {
        return Objects.isNull(this.httpCached) ? this.httpCached = this.load(new LinkedHashSet<>()) : this.httpCached;
    }

    /**
     * 收集 Scratches 目录中的 HTTP 请求文件
     * <p>
     * 遍历 Scratches 目录及其子目录, 收集所有 HTTP 请求文件, 并将它们添加到目标集合中.
     * @param httpFiles 目标集合, 用于存储收集到的 HTTP 请求文件
     * @return 收集到的 HTTP 请求文件集合
     */
    private SequencedCollection<HttpRequestItem> load(final SequencedCollection<HttpRequestItem> httpFiles) {
        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                Opt.ofNullable(ScratchFileService.getInstance().getVirtualFile(ScratchRootType.getInstance()))
                        .filter(item -> Objects.requireNonNull(item).exists())
                        .ifPresent(item -> this.walkDirectory(Objects.requireNonNull(item).toNioPath(), httpFiles, 3));
            } catch (final Exception ignored) {
                // 忽略任何异常，不中断执行
            }
        });
        return httpFiles;
    }

    /**
     * 遍历目录收集 HTTP 文件
     * <p>
     * 递归遍历指定目录及其子目录, 收集所有扩展名为 "http" 的文件, 并将它们添加到目标集合中.
     * @param directory 目录路径
     * @param httpFiles 目标集合, 用于存储找到的 HTTP 文件信息
     * @param maxDepth  最大遍历深度
     * @throws NullPointerException 如果 directory 或 httpFiles 为 null
     */
    @SuppressWarnings("SameParameterValue")
    private void walkDirectory(@NotNull final Path directory, final SequencedCollection<HttpRequestItem> httpFiles, final int maxDepth) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) return;
        try {
            Files.walkFileTree(directory, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<>() {
                /**
                 * 访问文件时处理 HTTP 文件
                 * <p>
                 * 遍历文件系统中的文件, 当遇到扩展名为 "http" 的文件时, 将其添加到 HTTP 文件列表中
                 * @param file  当前访问的文件路径
                 * @param attrs 文件属性
                 * @return 继续遍历文件系统
                 * @throws IOException 如果发生 I/O 错误
                 */
                @Override
                public @NotNull FileVisitResult visitFile(final @NotNull Path file, final @NotNull BasicFileAttributes attrs) throws IOException {
                    if (Files.isRegularFile(file) && FileUtilRt.extensionEquals(file.toString(), "http")) {
                        httpFiles.add(new HttpRequestItem(file.getFileName().toString(), file.toString()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                /**
                 * 处理文件访问失败的情况
                 * <p>
                 * 当访问文件时发生错误, 此方法会被调用. 根据错误情况决定是否继续遍历文件系统.
                 * @param file 发生错误的文件路径
                 * @param exc  引起访问失败的异常
                 * @return 文件访问结果, 如果继续遍历则返回 {@link FileVisitResult#CONTINUE}
                 */
                @Override
                public @NotNull FileVisitResult visitFileFailed(final @NotNull Path file, final @NotNull IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException ignored) {
            // 忽略IO异常，继续执行
        }
    }

    /**
     * 添加Git仓库到缓存
     * @param repositoryUrl 仓库URL
     */
    public void addGitRepository(final String repositoryUrl) {
        if (StrUtil.isBlank(repositoryUrl) || !ProjectNavigationItem.GIT_URL_PATTERN.matcher(repositoryUrl).matches()) {
            return;
        }
        synchronized (this.gitRepositoryCache) {
            this.gitRepositoryCache.put(repositoryUrl, System.currentTimeMillis());
        }
        this.cached = null;
    }

    /**
     * 移除Git仓库到缓存
     * @param repositoryUrl 仓库URL
     */
    public void removeGitRepository(final String repositoryUrl) {
        synchronized (this.gitRepositoryCache) {
            this.gitRepositoryCache.remove(repositoryUrl);
        }
        this.cached = null;
    }

    /**
     * 创建Git仓库导航项
     * @param url 仓库URL
     * @return 导航项
     */
    private ProjectNavigationItem createGitRepositoryItem(final String url) {
        final String[] orgRepo = extractOrgAndRepo(url);
        return ProjectNavigationItem.gitRepository(url, "%s/%s".formatted(orgRepo[0], orgRepo[1]), System.currentTimeMillis());
    }

    @Override
    public void dispose() {
        this.cached = null;
        this.httpCached = null;
        this.gitRepositoryCache.clear();
    }
}