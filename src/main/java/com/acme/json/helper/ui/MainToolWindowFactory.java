package com.acme.json.helper.ui;

import com.acme.json.helper.ui.editor.JsonEditor;
import com.acme.json.helper.ui.panel.PanelFunction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * json-helper工具窗口
 * @author 拒绝者
 * @date 2025-01-18
 */
public class MainToolWindowFactory implements ToolWindowFactory, DumbAware {
    /**
     * 标签计数器
     */
    private static final AtomicInteger tabCounter = new AtomicInteger(0);
    /**
     * 加载资源文件
     */
    private static final ResourceBundle bundle = ResourceBundle.getBundle("messages.JsonHelperBundle");

    /**
     * 创建工具窗口内容
     * @param project    项目
     * @param toolWindow 工具窗口
     */
    @Override
    public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
        ApplicationManager.getApplication().invokeLater(() -> {
            // 创建初始页签
            createNewTab(project, toolWindow);
            // 绑定活动分组到窗口
            toolWindow.setTitleActions(List.of(createActionGroup(project, toolWindow)));
        });
    }

    /**
     * 创建新的页签
     * @param project    项目
     * @param toolWindow 工具窗口
     */
    private void createNewTab(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
        // 增加页签号数
        final int number = tabCounter.incrementAndGet();
        // 创建页签内容面板
        final JPanel contentPanel = createWindowContent(project, number);
        // 创建页签内容
        final Content content = ContentFactory.getInstance().createContent(contentPanel, String.valueOf(number), Boolean.FALSE);
        content.setCloseable(Boolean.TRUE);
        // 页签关闭时释放资源
        content.setDisposer(() ->
                Arrays.stream(contentPanel.getComponents())
                        .filter(Objects::nonNull)
                        .filter(EditorTextField.class::isInstance)
                        .map(comp -> ((EditorTextField) comp).getEditor()).filter(Objects::nonNull)
                        .forEach(editor -> EditorFactory.getInstance().releaseEditor(editor))
        );
        // 将页签内容添加到工具窗口
        toolWindow.getContentManager().addContent(content);
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
                // 检查项目和窗口是否有效
                if (project.isDisposed() || toolWindow.isDisposed()) return;
                // 创建新页签
                createNewTab(project, toolWindow);
            }
        });
        return actionGroup;
    }

    /**
     * 创建窗口内容
     * @param project 项目
     * @param number  页签号数
     * @return {@link JPanel }
     */
    private JPanel createWindowContent(@NotNull final Project project, final int number) {
        // 窗口工具
        final JPanel toolWindowContent = new JPanel(new BorderLayout());
        // JSON编辑器
        final EditorTextField editor = new JsonEditor().create(project, number);
        // 将JSON编辑框添加到主面板
        toolWindowContent.add(editor, BorderLayout.CENTER);
        // 等待编辑器初始化后，挂载面板功能
        SwingUtilities.invokeLater(() -> {
            // 将面板功能添加到主面板
            toolWindowContent.add(new PanelFunction().create(editor), BorderLayout.NORTH);
            toolWindowContent.revalidate();
            toolWindowContent.repaint();
        });
        return toolWindowContent;
    }
}