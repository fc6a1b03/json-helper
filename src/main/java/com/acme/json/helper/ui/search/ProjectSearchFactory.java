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
     * 创建搜索提供者
     * @param initEvent init事件
     * @return {@link SearchEverywhereContributor }<{@link ProjectNavigationItem }>
     */
    @Override
    public @NotNull SearchEverywhereContributor<ProjectNavigationItem> createContributor(@NotNull final AnActionEvent initEvent) {
        return new ProjectSearch();
    }
}