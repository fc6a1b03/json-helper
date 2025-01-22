package com.acme.json.helper.ui;

import com.acme.json.helper.core.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 面板功能
 * @author 拒绝者
 * @date 2025-01-19
 */
public class PanelFunction {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 撤销历史堆栈
     */
    private final Deque<String> undoStack = new ArrayDeque<>();
    /**
     * 重做历史堆栈
     */
    private final Deque<String> redoStack = new ArrayDeque<>();
    /**
     * 原始记录`用于JSON搜索`
     */
    private final AtomicReference<String> originalJson = new AtomicReference<>("");

    /**
     * 创建面板功能
     * @param editor 当前编辑
     * @return {@link JPanel }
     */
    public JPanel create(final EditorTextField editor) {
        // 创建面板功能
        final JPanel searchPanel = new JPanel(new BorderLayout());
        // 搜索框
        final JTextField searchBox = new JTextField();
        searchBox.setPreferredSize(new Dimension(220, 20));
        searchBox.setToolTipText(BUNDLE.getString("json.tool.tip.text"));
        // 按钮组`撤消按钮`、`重做按钮`、`清除按钮`
        final JButton undoButton = createButton(AllIcons.Actions.Undo);
        final JButton redoButton = createButton(AllIcons.Actions.Redo);
        final JButton clearButton = createButton(AllIcons.Actions.ClearCash);
        // 添加按钮面板
        final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(undoButton);
        buttonPanel.add(redoButton);
        buttonPanel.add(clearButton);
        searchPanel.add(buttonPanel, BorderLayout.EAST);
        searchPanel.add(searchBox, BorderLayout.CENTER);
        // 编辑器动作
        editorAction(searchBox, redoButton, undoButton, editor);
        // 编辑器监听
        listener(editor, undoButton, redoButton, clearButton);
        return searchPanel;
    }

    /**
     * 创建标准化按钮
     * @param icon 按钮图标
     * @return 配置好的JButton实例
     */
    private JButton createButton(final Icon icon) {
        final JButton button = new JButton();
        button.setIcon(icon);
        button.setEnabled(Boolean.FALSE);
        button.setPreferredSize(new Dimension(35, 35));
        return button;
    }

    /**
     * 更新按钮可用状态
     * @param undoButton 撤销按钮
     * @param redoButton 重做按钮
     */
    private void updateButtons(final JButton undoButton, final JButton redoButton) {
        undoButton.setEnabled(Boolean.FALSE.equals(undoStack.isEmpty()));
        redoButton.setEnabled(Boolean.FALSE.equals(redoStack.isEmpty()));
    }

    /**
     * 重做
     * @param redoButton 重做按钮
     * @param undoButton 撤消按钮
     * @param editor     当前编辑
     */
    private void redoLastSearch(final JButton redoButton, final JButton undoButton, final EditorTextField editor) {
        if (Objects.isNull(editor) || redoStack.isEmpty()) return;
        // 储存撤销历史
        undoStack.push(editor.getDocument().getText());
        // 将重做历史写回编辑器
        editor.setText(redoStack.pop());
        // 更新按钮可用状态
        updateButtons(undoButton, redoButton);
    }

    /**
     * 撤消
     * @param redoButton 重做按钮
     * @param undoButton 撤消按钮
     * @param editor     当前编辑
     */
    private void undoLastSearch(final JButton redoButton, final JButton undoButton, final EditorTextField editor) {
        if (Objects.isNull(editor) || undoStack.isEmpty()) {
            originalJson.set("");
            return;
        }
        // 储存重做历史
        redoStack.push(editor.getDocument().getText());
        // 将撤消历史写回编辑器
        editor.setText(undoStack.pop());
        // 更新按钮可用状态
        updateButtons(undoButton, redoButton);
    }

    /**
     * 清空内容
     * @param editor 当前编辑
     */
    private void clearContent(final JButton redoButton, final JButton undoButton, final EditorTextField editor) {
        if (Objects.isNull(editor)) return;
        // 储存撤消历史
        undoStack.push(editor.getDocument().getText());
        // 清空原始记录
        originalJson.set("");
        // 清空重做历史
        redoStack.clear();
        // 清空编辑器
        editor.setText("");
        // 更新按钮可用状态
        updateButtons(undoButton, redoButton);
    }

    /**
     * 执行搜索
     * @param searchField 搜索字段
     * @param editor      当前编辑
     */
    private void performSearch(final JTextField searchField, final JButton redoButton,
                               final JButton undoButton, final EditorTextField editor) {
        if (Objects.isNull(editor)) return;
        final String searchExpression = searchField.getText();
        if (searchExpression.isEmpty()) return;
        final Document document = editor.getDocument();
        if (document.getText().isEmpty()) return;
        // 储存原始记录
        originalJson.compareAndSet("", document.getText());
        // 储存撤销历史
        undoStack.push(document.getText());
        // 搜索结果写回编辑器
        editor.setText(new JsonSearchEngine().process(originalJson.get(), searchExpression));
        // 更新按钮可用状态
        updateButtons(undoButton, redoButton);
    }

    /**
     * 显示编辑器上下文菜单
     * @param redoButton 重做按钮
     * @param undoButton 撤消按钮
     * @param editor     编辑器
     * @param e          鼠标事件
     */
    private void showEditorPopupMenu(final JButton redoButton, final JButton undoButton,
                                     final EditorTextField editor, final MouseEvent e) {
        final DefaultActionGroup group = new DefaultActionGroup();
        // 格式化菜单
        addJsonAction(group, "json.format.json", "json.format.json.desc",
                AllIcons.Actions.Refresh, new JsonFormatter(), redoButton, undoButton, editor);
        // 压缩菜单
        addJsonAction(group, "json.compress.json", "json.compress.json.desc",
                AllIcons.Actions.Collapseall, new JsonCompressor(), redoButton, undoButton, editor);
        // 转义菜单
        addJsonAction(group, "json.escaping.json", "json.escaping.json.desc",
                AllIcons.Javaee.UpdateRunningApplication, new JsonEscaper(), redoButton, undoButton, editor);
        // 去转义菜单
        addJsonAction(group, "json.un.escaping.json", "json.un.escaping.json.desc",
                AllIcons.Actions.SearchNewLine, new JsonUnEscaper(), redoButton, undoButton, editor);
        // 分隔符
        group.addSeparator();
        // 其他可适配的菜单
        group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_EDITOR_POPUP));
        // 添加菜单触发事件及出现位置
        ActionManager.getInstance()
                .createActionPopupMenu("JsonEditorPopup", group)
                .getComponent()
                .show(editor.getComponent(), e.getX(), e.getY());
    }

    /**
     * 添加JSON处理操作
     * @param group     Action组
     * @param nameKey   名称键
     * @param descKey   描述键
     * @param icon      图标
     * @param operation JSON操作
     */
    private void addJsonAction(final DefaultActionGroup group, final String nameKey, final String descKey,
                               final Icon icon, final JsonOperation operation, final JButton redoButton,
                               final JButton undoButton, final EditorTextField editor) {
        group.add(new AnAction(BUNDLE.getString(nameKey), BUNDLE.getString(descKey), icon) {
            @Override
            public void actionPerformed(final @NotNull AnActionEvent e) {
                optJson(redoButton, undoButton, editor.getEditor(), operation);
            }
        });
    }

    /**
     * 编辑器监听
     * @param editor      编辑器
     * @param undoButton  撤消按钮
     * @param redoButton  重做按钮
     * @param clearButton 清除按钮
     */
    private void listener(final EditorTextField editor, final JButton undoButton, final JButton redoButton, final JButton clearButton) {
        // 编辑器事件
        clearButton.addActionListener(e -> clearContent(redoButton, undoButton, editor));
        undoButton.addActionListener(e -> undoLastSearch(redoButton, undoButton, editor));
        redoButton.addActionListener(e -> redoLastSearch(redoButton, undoButton, editor));
        // 激活清空按钮
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(final @NotNull DocumentEvent e) {
                clearButton.setEnabled(Boolean.FALSE.equals(e.getDocument().getText().isEmpty()));
            }
        });
        // 上下文菜单
        editor.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showEditorPopupMenu(redoButton, undoButton, editor, e);
            }
        });
    }

    /**
     * 编辑器动作
     * @param redoButton 重做按钮
     * @param undoButton 撤消按钮
     * @param editor     编辑器
     */
    private void editorAction(final JTextField searchField, final JButton redoButton, final JButton undoButton, final EditorTextField editor) {
        // 右键菜单事件
        new AnAction() {
            @Override
            public void actionPerformed(final @NotNull AnActionEvent e) {
                optJson(redoButton, undoButton, editor.getEditor(), new JsonFormatter());
            }
        }.registerCustomShortcutSet(
                new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("ReformatCode")),
                editor.getComponent()
        );
        // 搜索框快捷事件
        new AnAction() {
            @Override
            public void actionPerformed(final @NotNull AnActionEvent e) {
                performSearch(searchField, redoButton, undoButton, editor);
            }
        }.registerCustomShortcutSet(
                new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)),
                searchField
        );
    }

    /**
     * 操作JSON
     * @param redoButton 重做按钮
     * @param undoButton 撤消按钮
     * @param editor     编辑器
     * @param operation  操作
     */
    private void optJson(final JButton redoButton, final JButton undoButton,
                         final Editor editor, final JsonOperation operation) {
        if (Objects.isNull(editor) || Objects.isNull(editor.getProject())) return;
        final Document document = editor.getDocument();
        if (StringUtil.isEmpty(document.getText())) return;
        WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> {
            // 储存撤销历史
            undoStack.push(document.getText());
            // 将操作结果写回编辑器
            document.setText(StringUtil.convertLineSeparators(operation.process(document.getText())));
            // 更新按钮可用状态
            updateButtons(undoButton, redoButton);
        });
    }
}