package com.acme.json.helper.core.search.cache;

import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.search.item.ProjectNavigationItem;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

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
public final class SearchCache implements Supplier<SequencedCollection<ProjectNavigationItem>> {
    /**
     * Git仓库缓存 (使用LRU策略，最多保留20个)
     */
    private final LinkedHashMap<String, Long> gitRepositoryCache = new LinkedHashMap<>(20, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Long> eldest) {
            return this.size() > 20;
        }
    };
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
}