package com.acme.json.helper.ui.search.cache;

import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.ui.search.item.ProjectNavigationItem;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import java.util.*;
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
     * 缓存
     */
    private SequencedCollection<ProjectNavigationItem> cached;

    /**
     * 获取缓存项目
     * @return {@link SequencedCollection }<{@link ProjectNavigationItem }>
     */
    @Override
    public SequencedCollection<ProjectNavigationItem> get() {
        return Objects.isNull(this.cached) ? this.cached = this.load() : this.cached;
    }

    /**
     * 加载缓存项目
     * @return {@link SequencedCollection }<{@link ProjectNavigationItem }>
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
                .map(name -> ProjectNavigationItem.recent(((RecentProjectsManagerBase) RecentProjectsManager.getInstance()).getProjectName(name), name, 0));
        // 合并去重并保持顺序
        return Stream.concat(opened, recent).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}