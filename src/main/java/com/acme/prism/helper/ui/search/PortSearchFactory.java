package com.acme.prism.ui.search;

import com.acme.prism.core.search.PortSearch;
import com.acme.prism.core.search.item.PortSearchItem;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 端口搜索工厂<br/>
 * 用于创建端口进程搜索贡献者
 *
 * @author 拒绝者
 * @date 2026-04-01
 */
public class PortSearchFactory implements SearchEverywhereContributorFactory<PortSearchItem> {
    /**
     * 创建端口搜索贡献者
     * <p>
     * 根据传入的动作事件创建一个端口进程搜索贡献者实例
     *
     * @param event 动作事件, 用于获取项目信息
     * @return 返回一个新的端口进程搜索贡献者实例
     */
    @Override
    public @NotNull SearchEverywhereContributor<PortSearchItem> createContributor(@NotNull final AnActionEvent event) {
        return new PortSearch(event.getProject());
    }
}
