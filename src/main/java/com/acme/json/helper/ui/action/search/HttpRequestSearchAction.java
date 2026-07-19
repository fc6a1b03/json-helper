package com.acme.json.helper.ui.action.search;

import com.acme.json.helper.core.search.HttpRequestSearch;
import com.acme.json.helper.core.settings.PluginSettings;
import com.intellij.ide.actions.SearchEverywhereBaseAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * HTTP搜索操作
 * @author 拒绝者
 * @date 2025-11-05
 */
public class HttpRequestSearchAction extends SearchEverywhereBaseAction implements DumbAware {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        // 设置面板关闭本搜索时禁用入口
        event.getPresentation().setEnabled(PluginSettings.of().httpSearchEnabled);
    }

    /**
     * 执行动作
     * @param event 事件
     */
    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        this.showInSearchEverywherePopup(HttpRequestSearch.PROVIDER_ID, event, Boolean.FALSE, Boolean.FALSE);
    }
}