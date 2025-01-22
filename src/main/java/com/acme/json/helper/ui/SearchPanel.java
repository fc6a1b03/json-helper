package com.acme.json.helper.ui;

import com.acme.json.helper.core.JsonSearchEngine;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
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
     * 撤销历史堆栈
     */
    private final Stack<String> undoStack = new Stack<>();
    /**
     * 重做历史堆栈
     */
    private final Stack<String> redoStack = new Stack<>();
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
        // 创建搜索面板
        final JPanel searchPanel = new JPanel(new BorderLayout());
        // 搜索字段
        final JTextField searchField = new JTextField();
        searchField.setSize(new Dimension(200, 15));
        searchField.setToolTipText(BUNDLE.getString("json.tool.tip.text"));
        // 撤销按钮
        final JButton undoButton = new JButton();
        undoButton.setEnabled(Boolean.FALSE);
        undoButton.setIcon(AllIcons.Actions.Undo);
        undoButton.setSize(new Dimension(5, 15));
        final JButton redoButton = new JButton();
        redoButton.setEnabled(Boolean.FALSE);
        redoButton.setIcon(AllIcons.Actions.Redo);
        redoButton.setSize(new Dimension(5, 15));
        // 清空按钮
        final JButton clearButton = new JButton();
        clearButton.setEnabled(Boolean.FALSE);
        clearButton.setIcon(AllIcons.Actions.ClearCash);
        clearButton.setSize(new Dimension(5, 15));
        // 搜索框快捷事件
        new AnAction() {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                performSearch(searchField, redoButton, undoButton, currentEditor);
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), searchField);
        // 存在内容的时候激活清空按钮
        currentEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull final DocumentEvent e) {
                if (StringUtil.isNotEmpty(e.getDocument().getText())) {
                    clearButton.setEnabled(Boolean.TRUE);
                }
            }
        });
        // 添加按钮面板
        final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(undoButton);
        buttonPanel.add(redoButton);
        buttonPanel.add(clearButton);
        searchPanel.add(buttonPanel, BorderLayout.EAST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        // 清空按钮点击事件
        clearButton.addActionListener(e -> clearContent(redoButton, undoButton, currentEditor));
        // 撤销按钮点击事件
        undoButton.addActionListener(e -> undoLastSearch(redoButton, undoButton, currentEditor));
        redoButton.addActionListener(e -> redoLastSearch(redoButton, undoButton, currentEditor));
        return searchPanel;
    }

    /**
     * 重做
     * @param redoButton    重做按钮
     * @param undoButton    撤消按钮
     * @param currentEditor 当前编辑
     */
    private void redoLastSearch(final JButton redoButton, final JButton undoButton, final EditorTextField currentEditor) {
        if (Objects.isNull(currentEditor) || redoStack.isEmpty()) return;
        // 储存撤销历史
        undoStack.push(currentEditor.getDocument().getText());
        // 恢复上一个版本的内容
        currentEditor.setText(redoStack.pop());
        // 如果历史栈为空，禁用撤销按钮
        if (redoStack.isEmpty()) {
            redoButton.setEnabled(Boolean.FALSE);
        }
        undoButton.setEnabled(Boolean.TRUE);
    }

    /**
     * 撤消
     * @param redoButton    重做按钮
     * @param undoButton    撤消按钮
     * @param currentEditor 当前编辑
     */
    private void undoLastSearch(final JButton redoButton, final JButton undoButton, final EditorTextField currentEditor) {
        if (Objects.isNull(currentEditor) || undoStack.isEmpty()) {
            // 清空原始
            originalJson.set("");
            return;
        }
        // 储存重做历史
        redoStack.push(currentEditor.getDocument().getText());
        // 恢复上一个版本的内容
        currentEditor.setText(undoStack.pop());
        // 如果历史栈为空，禁用撤销按钮
        if (undoStack.isEmpty()) {
            undoButton.setEnabled(Boolean.FALSE);
        }
        redoButton.setEnabled(Boolean.TRUE);
    }

    /**
     * 清空内容
     * @param currentEditor 当前编辑
     */
    private void clearContent(final JButton redoButton, final JButton undoButton, final EditorTextField currentEditor) {
        if (Objects.isNull(currentEditor)) return;
        // 储存撤销历史
        undoStack.push(currentEditor.getDocument().getText());
        // 清空原始
        originalJson.set("");
        // 清空操作内容
        redoStack.clear();
        // 清空内容
        currentEditor.setText("");
        // 启用撤销按钮
        undoButton.setEnabled(Boolean.TRUE);
        // 禁用重做按钮
        redoButton.setEnabled(Boolean.FALSE);
    }


    /**
     * 执行搜索
     * @param searchField   搜索字段
     * @param currentEditor 当前编辑
     */
    private void performSearch(final JTextField searchField, final JButton redoButton, final JButton undoButton, final EditorTextField currentEditor) {
        if (Objects.isNull(currentEditor)) return;
        final String searchExpression = searchField.getText();
        if (searchExpression.isEmpty()) return;
        // 储存原始
        if (StringUtil.isEmpty(originalJson.get())) {
            originalJson.set(currentEditor.getDocument().getText());
        }
        // 储存撤销历史
        undoStack.push(currentEditor.getDocument().getText());
        // 格式化并重新写入
        currentEditor.setText(
                new JsonSearchEngine().process(
                        originalJson.get(),
                        searchExpression
                )
        );
        // 启用撤销按钮
        undoButton.setEnabled(Boolean.TRUE);
        // 禁用重做按钮
        redoButton.setEnabled(Boolean.FALSE);
    }
}