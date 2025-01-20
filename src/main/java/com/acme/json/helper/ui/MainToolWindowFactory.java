package com.acme.json.helper.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ResourceBundle;

/**
 * JSON助手工具窗口
 * @author 拒绝者
 * @date 2025-01-18
 */
public class MainToolWindowFactory implements ToolWindowFactory, DumbAware {
    /**
     * 加载资源文件
     */
    private static final ResourceBundle bundle = ResourceBundle.getBundle("messages.JsonHelperBundle");
    private static int tabCounter = 1;

    /**
     * 创建工具窗口内容
     * @param project    项目
     * @param toolWindow 工具窗口
     */
    @Override
    public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                // 创建初始页签
                createNewTab(project, toolWindow);
                // 创建活动分组
                final List<DefaultActionGroup> actionGroup = List.of(createActionGroup(project, toolWindow));
                // 将按钮添加到工具窗口的工具栏
                toolWindow.setTitleActions(actionGroup);
            });
        });
    }

    /**
     * 创建新的页签
     * @param project    项目
     * @param toolWindow 工具窗口
     */
    private void createNewTab(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
        final Content content = ContentFactory.getInstance()
                .createContent(
                        createWindowContent(project), String.valueOf(tabCounter++), Boolean.FALSE
                );
        content.setCloseable(Boolean.TRUE);
        toolWindow.setShowStripeButton(Boolean.TRUE);
        toolWindow.getContentManager().addContent(content);
        toolWindow.getContentManager().setSelectedContent(content);
    }

    /**
     * 创建活动分组
     * @param project    项目
     * @param toolWindow 工具窗口
     * @return {@link DefaultActionGroup }
     */
    private DefaultActionGroup createActionGroup(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
        final DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new AnAction(bundle.getString("json.new.tab"), bundle.getString("json.new.tab.desc"), AllIcons.General.Add) {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                createNewTab(project, toolWindow);
            }
        });
        return actionGroup;
    }

    /**
     * 创建窗口内容
     * @return {@link JPanel }
     */
    private JPanel createWindowContent(@NotNull final Project project) {
        // 窗口工具
        final JPanel toolWindowContent = new JPanel(new BorderLayout());
        // JSON编辑器
        final EditorTextField currentEditor = new JsonEditor().create(project);
        // 将JSON编辑框添加到主面板
        toolWindowContent.add(currentEditor, BorderLayout.CENTER);
        // 将搜索面板添加到主面板
        toolWindowContent.add(new SearchPanel().create(currentEditor), BorderLayout.NORTH);
        // 激活编辑器组件
        ToolWindowManager.getInstance(project).activateEditorComponent();
        return toolWindowContent;
    }
}