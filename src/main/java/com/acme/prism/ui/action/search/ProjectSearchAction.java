package com.acme.prism.ui.action.search;

import com.acme.prism.core.search.ProjectSearch;
import com.acme.prism.core.settings.PluginSettings;
import com.intellij.ide.actions.SearchEverywhereBaseAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.util.ResourceBundle;

/**
 * 项目搜索操作
 * @author 拒绝者
 * @date 2025-11-05
 */
public class ProjectSearchAction extends SearchEverywhereBaseAction implements DumbAware {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.PrismBundle");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        // 菜单文本（2026.2 起空菜单文本强制抛 PluginException；随设置关闭本搜索时禁用入口）
        event.getPresentation().setText(BUNDLE.getString("action.project.search.text"));
        event.getPresentation().setEnabled(PluginSettings.of().projectSearchEnabled);
    }

    /**
     * 执行动作
     * @param event 事件
     */
    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        this.showInSearchEverywherePopup(ProjectSearch.PROVIDER_ID, event, Boolean.FALSE, Boolean.FALSE);
    }
}