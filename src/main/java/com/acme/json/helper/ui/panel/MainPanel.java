package com.acme.json.helper.ui.panel;

import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.json.*;
import com.acme.json.helper.core.parser.PathParser;
import com.acme.json.helper.ui.dialog.ConvertJavaDialog;
import com.acme.json.helper.ui.notice.Notifier;
import com.alibaba.fastjson2.JSON;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 主面板
 * @author 拒绝者
 * @date 2025-01-19
 */
public class MainPanel {
    /** 加载语言资源文件 */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /** 撤销历史堆栈 */
    private final Deque<String> undoStack = new ArrayDeque<>();
    /** 重做历史堆栈 */
    private final Deque<String> redoStack = new ArrayDeque<>();
    /** 原始记录`用于JSON搜索` */
    private final AtomicReference<String> originalJson = new AtomicReference<>("");

    /**
     * 创建主面板
     * @param editor 当前编辑
     * @return {@link JPanel }
     */
    public JPanel create(final EditorTextField editor) {
        // 创建主面板
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
        undoButton.setEnabled(!undoStack.isEmpty());
        redoButton.setEnabled(!redoStack.isEmpty());
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
        // 转为Java类
        addJsonToJavaAction(group, editor);
        // 打开本地JSON文件
        addOpenFileAction(group, editor);
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
     * 添加JSONToJAVA操作
     * @param group Action组
     */
    private void addJsonToJavaAction(final DefaultActionGroup group, final EditorTextField editor) {
        group.add(new AnAction(
                BUNDLE.getString("json.to.java"),
                BUNDLE.getString("json.to.java.desc"),
                AllIcons.Debugger.Db_muted_dep_line_breakpoint
        ) {
            @Override
            public void actionPerformed(final @NotNull AnActionEvent e) {
                if (Objects.isNull(editor) || Objects.isNull(editor.getProject())) return;
                final Document document = editor.getDocument();
                if (!JSON.isValid(document.getText())) return;
                if (!JSON.isValidObject(document.getText())) {
                    Notifier.notifyWarn(BUNDLE.getString("json.to.bean.warn"), editor.getProject());
                    return;
                }
                // 激活弹窗
                ApplicationManager.getApplication().invokeLater(() -> new ConvertJavaDialog(editor.getProject(), document.getText()).show());
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
                // 文档内容
                final String text = e.getDocument().getText().trim();
                // 根据文档内容调整清空按钮的状态
                clearButton.setEnabled(!StrUtil.isEmpty(text));
                // 自动识别`web路径`或`本地文件路径`转为JSON，写回编辑器
                OptPath(text, editor, e, this);
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
     * 自动识别路径类型（Web或本地路径）并将其转换为格式化JSON，回写到编辑器<br/>
     * 处理过程采用异步方式以避免阻塞UI线程，包含完整的异常处理和用户反馈
     *
     * @param text    当前编辑器中的原始文本内容
     * @param editor  目标编辑器组件，用于回写处理结果
     * @param e       文档变更事件对象，用于操作关联的文档
     * @param listener 文档监听器，处理过程中需要临时解除绑定避免循环触发
     */
    private void OptPath(final String text,
                         final EditorTextField editor,
                         final @NotNull DocumentEvent e,
                         final @NotNull DocumentListener listener) {
        // 异步执行路径转换任务
        final CompletableFuture<String> convertPathToJson = PathParser.convertPathToJson(text);
        if (Objects.nonNull(convertPathToJson)) {
            convertPathToJson
                    // 异步处理完成后的回调（UI线程执行）
                    .thenAccept(processedText -> ApplicationManager.getApplication().invokeLater(() -> {
                        // 验证JSON格式有效性
                        if (JSON.isValid(processedText)) {
                            // 临时移除文档监听器，避免文本变更的循环触发
                            e.getDocument().removeDocumentListener(listener);
                            try {
                                // 清空原始记录
                                originalJson.set("");
                                // 使用JSON格式化工具美化输出，并更新编辑器内容
                                editor.setText(new JsonFormatter().process(processedText));
                            } finally {
                                // 确保无论成功与否都重新注册监听器
                                e.getDocument().addDocumentListener(listener);
                            }
                        } else {
                            Notifier.notifyError(BUNDLE.getString("path.to.json.warn"), editor.getProject());
                        }
                    }))
                    // 异常处理流程
                    .exceptionally(ex -> {
                        ApplicationManager.getApplication().invokeLater(() ->
                                Notifier.notifyError(BUNDLE.getString("path.to.json.warn"), editor.getProject())
                        );
                        return null;
                    });
        }
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
        if (!JSON.isValid(document.getText())) return;
        WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> {
            // 储存撤销历史
            undoStack.push(document.getText());
            // 将操作结果写回编辑器
            document.setText(StringUtil.convertLineSeparators(operation.process(document.getText())));
            // 更新按钮可用状态
            updateButtons(undoButton, redoButton);
        });
    }

    /**
     * 添加打开JSON文件操作
     *
     * @param group 默认操作组
     * @param editor 编辑器
     */
    private void addOpenFileAction(final DefaultActionGroup group, final EditorTextField editor) {
        group.add(new AnAction(
                BUNDLE.getString("menu.open.json.file"),
                BUNDLE.getString("menu.open.json.file.desc"),
                AllIcons.Actions.MenuOpen
        ) {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                handleFileOpen(editor);
            }
        });
    }

    /**
     * 处理文件打开操作
     *
     * @param editor 编辑器
     */
    private void handleFileOpen(final EditorTextField editor) {
        FileChooser.chooseFile(
                FileChooserDescriptorFactory
                        .createSingleFileDescriptor(editor.getFileType())
                        .withFileFilter(virtualFile -> "json".equalsIgnoreCase(virtualFile.getExtension())),
                editor.getProject(), null, virtualFile -> {
                    if (Objects.isNull(virtualFile)) return;
                    try {
                        final String content = new String(virtualFile.contentsToByteArray(), StandardCharsets.UTF_8);
                        if (JSON.isValid(content)) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> {
                                    final String text = new JsonFormatter().process(content);
                                    // 将文本写入编辑器
                                    editor.getDocument().setText(text);
                                    // 储存原始记录
                                    originalJson.set(text);
                                    // 清空撤销、重做记录
                                    undoStack.clear();
                                    redoStack.clear();
                                });
                                Notifier.notifyInfo("%s%s".formatted(BUNDLE.getString("file.load.success"), virtualFile.getPath()), editor.getProject());
                            });
                        } else {
                            ApplicationManager.getApplication().invokeLater(() ->
                                    Notifier.notifyError(BUNDLE.getString("file.load.failed"), editor.getProject())
                            );
                        }
                    } catch (Exception ignored) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                Notifier.notifyError(BUNDLE.getString("file.load.failed"), editor.getProject())
                        );
                    }
                });
    }
}