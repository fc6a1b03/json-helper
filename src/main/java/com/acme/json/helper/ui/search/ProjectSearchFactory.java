package com.acme.json.helper.ui.search;

import com.acme.json.helper.core.search.ProjectSearch;
import com.acme.json.helper.core.search.item.ProjectNavigationItem;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 项目搜索工厂
 * @author 拒绝者
 * @date 2025-11-05
 */
public class ProjectSearchFactory implements SearchEverywhereContributorFactory<ProjectNavigationItem> {
    /**
     * 创建项目导航搜索贡献者
     * <p>
     * 根据传入的动作事件创建一个项目搜索贡献者实例
     * @param event 动作事件, 用于获取项目信息
     * @return 返回一个新的项目搜索贡献者实例
     */
    @Override
    public @NotNull SearchEverywhereContributor<ProjectNavigationItem> createContributor(@NotNull final AnActionEvent event) {
        return new ProjectSearch(event.getProject());
    }
}