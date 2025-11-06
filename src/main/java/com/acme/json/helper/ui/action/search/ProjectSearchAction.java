package com.acme.json.helper.ui.action.search;

import com.intellij.ide.actions.SearchEverywhereBaseAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * 项目搜索操作
 * @author 拒绝者
 * @date 2025-11-05
 */
public class ProjectSearchAction extends SearchEverywhereBaseAction implements DumbAware {
    /**
     * 执行动作
     * @param event 事件
     */
    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        this.showInSearchEverywherePopup("ProjectSearch", event, Boolean.FALSE, Boolean.FALSE);
    }
}