package com.acme.json.helper.ui.search;

import com.acme.json.helper.core.archive.ArchiveSearch;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 压缩包搜索工厂<br/>
 * 用于创建压缩包内容搜索贡献者
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
public class ArchiveSearchFactory implements SearchEverywhereContributorFactory<ArchiveSearch.ArchiveEntryItem> {
    /**
     * 创建压缩包搜索贡献者
     *
     * @param event 动作事件, 用于获取项目信息
     * @return 返回一个新的压缩包搜索贡献者实例
     */
    @Override
    public @NotNull SearchEverywhereContributor<ArchiveSearch.ArchiveEntryItem> createContributor(@NotNull final AnActionEvent event) {
        return new ArchiveSearch(event.getProject());
    }
}
