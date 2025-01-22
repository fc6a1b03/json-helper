package com.acme.json.helper.ui;

import com.acme.json.helper.core.JsonSearchEngine;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 搜索面板
 * @author 拒绝者
 * @date 2025-01-19
 */
public class SearchPanel {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 原始记录`用于JSON搜索`
     */
    private final AtomicReference<String> originalJson = new AtomicReference<>("");

    /**
     * 创建搜索面板
     * @param currentEditor 当前编辑
     * @return {@link JPanel }
     */
    public JPanel create(final EditorTextField currentEditor) {
        // 历史堆栈
        final Stack<String> historyStack = new Stack<>();
        // 创建搜索面板
        final JPanel searchPanel = new JPanel(new BorderLayout());
        // 搜索字段
        final JTextField searchField = new JTextField();
        searchField.setSize(new Dimension(200, 20));
        searchField.setToolTipText(BUNDLE.getString("json.tool.tip.text"));
        // 撤销按钮
        final JButton undoButton = new JButton();
        undoButton.setEnabled(Boolean.FALSE);
        undoButton.setIcon(AllIcons.Javaee.UpdateRunningApplication);
        // 清空按钮
        final JButton clearButton = new JButton();
        clearButton.setIcon(AllIcons.Actions.ClearCash);
        // 搜索框快捷事件
        new AnAction() {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                performSearch(searchField, undoButton, historyStack, currentEditor);
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), searchField);
        // 添加按钮面板
        final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(undoButton);
        buttonPanel.add(clearButton);
        searchPanel.add(buttonPanel, BorderLayout.EAST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        // 清空按钮点击事件
        clearButton.addActionListener(e -> clearContent(undoButton, historyStack, currentEditor));
        // 撤销按钮点击事件
        undoButton.addActionListener(e -> undoLastSearch(undoButton, historyStack, currentEditor));
        return searchPanel;
    }

    /**
     * 撤消上次搜索
     * @param undoButton    撤消按钮
     * @param historyStack  历史堆栈
     * @param currentEditor 当前编辑
     */
    private void undoLastSearch(final JButton undoButton, final Stack<String> historyStack, final EditorTextField currentEditor) {
        if (Objects.isNull(currentEditor) || historyStack.isEmpty()) {
            // 清空原始
            originalJson.set("");
            return;
        }
        // 恢复上一个版本的内容
        currentEditor.setText(historyStack.pop());
        // 如果历史栈为空，禁用撤销按钮
        if (historyStack.isEmpty()) {
            undoButton.setEnabled(Boolean.FALSE);
        }
    }

    /**
     * 清空内容
     * @param historyStack  历史堆栈
     * @param currentEditor 当前编辑
     */
    private void clearContent(final JButton undoButton, final Stack<String> historyStack, final EditorTextField currentEditor) {
        if (Objects.isNull(currentEditor)) return;
        // 储存历史
        historyStack.push(currentEditor.getDocument().getText());
        // 清空原始
        originalJson.set("");
        // 清空内容
        currentEditor.setText("");
        // 启用撤销按钮
        undoButton.setEnabled(Boolean.TRUE);
    }


    /**
     * 执行搜索
     * @param searchField   搜索字段
     * @param currentEditor 当前编辑
     */
    private void performSearch(final JTextField searchField, final JButton undoButton, final Stack<String> historyStack, final EditorTextField currentEditor) {
        if (Objects.isNull(currentEditor)) return;
        final String searchExpression = searchField.getText();
        if (searchExpression.isEmpty()) return;
        // 储存原始
        if (StringUtil.isEmpty(originalJson.get())) {
            originalJson.set(currentEditor.getDocument().getText());
        }
        // 储存历史
        historyStack.push(currentEditor.getDocument().getText());
        // 格式化并重新写入
        currentEditor.setText(
                new JsonSearchEngine().process(
                        originalJson.get(),
                        searchExpression
                )
        );
        // 启用撤销按钮
        undoButton.setEnabled(Boolean.TRUE);
    }
}