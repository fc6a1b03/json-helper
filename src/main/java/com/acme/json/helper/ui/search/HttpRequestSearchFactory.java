package com.acme.json.helper.ui.search;

import com.acme.json.helper.core.search.HttpRequestSearch;
import com.acme.json.helper.core.search.item.HttpRequestItem;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * HTTP请求搜索工厂<br/>
 * 用于创建HTTP请求文件搜索贡献者
 * @author 拒绝者
 * @date 2025-11-05
 */
public class HttpRequestSearchFactory implements SearchEverywhereContributorFactory<HttpRequestItem> {
    /**
     * 创建HTTP请求搜索贡献者
     * <p>
     * 根据传入的动作事件创建一个HTTP请求搜索贡献者实例
     * @param event 动作事件, 用于获取项目信息
     * @return 返回一个新的HTTP请求搜索贡献者实例
     */
    @Override
    public @NotNull SearchEverywhereContributor<HttpRequestItem> createContributor(@NotNull final AnActionEvent event) {
        return new HttpRequestSearch(event.getProject());
    }
}