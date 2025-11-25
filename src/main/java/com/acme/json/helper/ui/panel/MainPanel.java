package com.acme.json.helper.ui.panel;

import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.Clipboard;
import com.acme.json.helper.core.json.*;
import com.acme.json.helper.core.notice.Notifier;
import com.acme.json.helper.core.parser.AnyParser;
import com.acme.json.helper.core.parser.JwtParser;
import com.acme.json.helper.core.parser.PathParser;
import com.acme.json.helper.ui.dialog.ConvertAnyDialog;
import com.alibaba.fastjson2.JSON;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
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
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
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
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    private static final Logger log = LoggerFactory.getLogger(MainPanel.class);
    /**
     * 重做历史堆栈
     */
    private final Deque<String> redoStack = new ArrayDeque<>();
    /**
     * 撤销历史堆栈
     */
    private final Deque<String> undoStack = new ArrayDeque<>();
    /**
     * 原始记录`用于JSON搜索`
     */
    private final AtomicReference<String> originalJson = new AtomicReference<>("");

    /**
     * 创建主面板
     * @param editor 当前编辑
     * @return {@link JPanel }
     */
    public JPanel create(final EditorTextField editor) {
        // 创建主面板
        final JPanel searchPanel = new JPanel(new BorderLayout(0, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder());
        // 搜索框
        final JTextField searchBox = this.createSearchBox();
        // 按钮组`撤消按钮`、`重做按钮`、`清除按钮`
        final JButton undoButton = this.createButton(AllIcons.Actions.Undo, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE);
        final JButton redoButton = this.createButton(AllIcons.Actions.Redo, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE);
        final JButton clearButton = this.createButton(AllIcons.Actions.ClearCash, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE);
        // 添加按钮面板
        final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder());
        buttonPanel.add(undoButton);
        buttonPanel.add(redoButton);
        buttonPanel.add(clearButton);
        searchPanel.add(buttonPanel, BorderLayout.EAST);
        searchPanel.add(searchBox, BorderLayout.CENTER);
        // 编辑器动作
        this.editorAction(searchBox, redoButton, undoButton, editor);
        // 编辑器监听
        this.listener(editor, undoButton, redoButton, clearButton);
        return searchPanel;
    }

    /**
     * 创建搜索框
     * @return {@link JTextField }
     */
    private JTextField createSearchBox() {
        final JTextField searchBox = new JTextField();
        searchBox.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(final FocusEvent e) {
                if (e.getSource() instanceof final JTextField field) {
                    field.setBorder(MainPanel.this.createBorderByDefaultColor());
                }
            }

            @Override
            public void focusLost(final FocusEvent e) {
                if (e.getSource() instanceof final JTextField field) {
                    field.setBorder(MainPanel.this.createBorderByDefaultColor());
                }
            }
        });
        searchBox.setMargin(JBUI.emptyInsets());
        searchBox.setBorder(MainPanel.this.createBorderByDefaultColor());
        searchBox.setPreferredSize(new Dimension(220, 35));
        searchBox.setToolTipText(BUNDLE.getString("json.tool.tip.text"));
        return searchBox;
    }

    /**
     * 创建标准化按钮
     * @param icon 按钮图标
     * @return 配置好的JButton实例
     */
    @SuppressWarnings("SameParameterValue")
    private JButton createButton(final Icon icon, final boolean top, final boolean left, final boolean bottom, final boolean right) {
        final JButton toolButton = new JButton();
        toolButton.setIcon(icon);
        toolButton.setEnabled(Boolean.FALSE);
        toolButton.setMargin(JBUI.emptyInsets());
        toolButton.setPreferredSize(new Dimension(35, 35));
        toolButton.setBorder(MainPanel.this.createBorderByDefaultColor(top, left, bottom, right));
        toolButton.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(final FocusEvent e) {
                if (e.getSource() instanceof final JButton button) {
                    button.setBorder(MainPanel.this.createBorderByDefaultColor(top, left, bottom, right));
                }
            }

            @Override
            public void focusLost(final FocusEvent e) {
                if (e.getSource() instanceof final JButton button) {
                    button.setBorder(MainPanel.this.createBorderByDefaultColor(top, left, bottom, right));
                }
            }
        });
        return toolButton;
    }

    /**
     * 按默认颜色创建边框`四周边框`
     * @return {@link Border }
     */
    private Border createBorderByDefaultColor() {
        return BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1);
    }

    /**
     * 按默认颜色创建边框`自定义边框`
     * @param top    顶端
     * @param left   左边
     * @param bottom 底部
     * @param right  正确
     * @return {@link Border }
     */
    private Border createBorderByDefaultColor(final boolean top, final boolean left, final boolean bottom, final boolean right) {
        return BorderFactory.createMatteBorder(
                top ? 1 : 0,
                left ? 1 : 0,
                bottom ? 1 : 0,
                right ? 1 : 0,
                UIManager.getColor("Component.borderColor")
        );
    }

    /**
     * 更新按钮可用状态
     * @param undoButton 撤销按钮
     * @param redoButton 重做按钮
     */
    private void updateButtons(final JButton undoButton, final JButton redoButton) {
        undoButton.setEnabled(!this.undoStack.isEmpty());
        redoButton.setEnabled(!this.redoStack.isEmpty());
    }

    /**
     * 重做
     * @param redoButton 重做按钮
     * @param undoButton 撤消按钮
     * @param editor     当前编辑
     */
    private void redoLastSearch(final JButton redoButton, final JButton undoButton, final EditorTextField editor) {
        if (Objects.isNull(editor) || this.redoStack.isEmpty()) return;
        // 储存撤销历史
        this.undoStack.push(editor.getDocument().getText());
        // 将重做历史写回编辑器
        editor.setText(this.redoStack.pop());
        // 更新按钮可用状态
        this.updateButtons(undoButton, redoButton);
    }

    /**
     * 撤消
     * @param redoButton 重做按钮
     * @param undoButton 撤消按钮
     * @param editor     当前编辑
     */
    private void undoLastSearch(final JButton redoButton, final JButton undoButton, final EditorTextField editor) {
        if (Objects.isNull(editor) || this.undoStack.isEmpty()) {
            this.originalJson.set("");
            return;
        }
        // 储存重做历史
        this.redoStack.push(editor.getDocument().getText());
        // 将撤消历史写回编辑器
        editor.setText(this.undoStack.pop());
        // 更新按钮可用状态
        this.updateButtons(undoButton, redoButton);
    }

    /**
     * 清空内容
     * @param editor 当前编辑
     */
    private void clearContent(final JButton redoButton, final JButton undoButton, final EditorTextField editor) {
        if (Objects.isNull(editor)) return;
        // 储存撤消历史
        this.undoStack.push(editor.getDocument().getText());
        // 清空原始记录
        this.originalJson.set("");
        // 清空重做历史
        this.redoStack.clear();
        // 清空编辑器
        editor.setText("");
        // 更新按钮可用状态
        this.updateButtons(undoButton, redoButton);
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
        this.originalJson.compareAndSet("", document.getText());
        // 储存撤销历史
        this.undoStack.push(document.getText());
        // 搜索结果写回编辑器
        editor.setText(new JsonSearchEngine().process(this.originalJson.get(), searchExpression));
        // 更新按钮可用状态
        this.updateButtons(undoButton, redoButton);
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
        this.addJsonAction(group, "json.format.json", "json.format.json.desc",
                AllIcons.Actions.Refresh, new JsonFormatter(), redoButton, undoButton, editor);
        // 压缩菜单
        this.addJsonAction(group, "json.compress.json", "json.compress.json.desc",
                AllIcons.Actions.Collapseall, new JsonCompressor(), redoButton, undoButton, editor);
        // 转义菜单
        this.addJsonAction(group, "json.escaping.json", "json.escaping.json.desc",
                AllIcons.Javaee.UpdateRunningApplication, new JsonEscaper(), redoButton, undoButton, editor);
        // 去转义菜单
        this.addJsonAction(group, "json.un.escaping.json", "json.un.escaping.json.desc",
                AllIcons.Actions.SearchNewLine, new JsonUnEscaper(), redoButton, undoButton, editor);
        // 修复菜单
        this.addJsonAction(group, "json.repair.json", "json.repair.json.desc",
                AllIcons.Toolwindows.ToolWindowBuild, new JsonRepairer(), redoButton, undoButton, editor);
        // 分隔符
        group.addSeparator();
        // 差异对比菜单
        this.addDiffAction(group, editor);
        // 转为任何
        this.addJsonToAnyAction(group, editor);
        // 添加打开文件菜单
        this.addOpenFileAction(group, editor);
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
                MainPanel.this.optJson(redoButton, undoButton, editor.getEditor(), operation);
            }
        });
    }

    /**
     * 添加JSONToAny操作
     * @param group Action组
     */
    private void addJsonToAnyAction(final DefaultActionGroup group, final EditorTextField editor) {
        group.add(new AnAction(
                BUNDLE.getString("json.to.any"),
                BUNDLE.getString("json.to.any.desc"),
                AllIcons.Debugger.Db_muted_dep_line_breakpoint
        ) {
            @Override
            public void actionPerformed(final @NotNull AnActionEvent e) {
                if (Objects.isNull(editor) || Objects.isNull(editor.getProject())) return;
                final Document document = editor.getDocument();
                if (!JSON.isValid(document.getText())) return;
                // 激活弹窗
                ApplicationManager.getApplication().invokeLater(() -> new ConvertAnyDialog(editor.getProject(), document.getText()).show());
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
        clearButton.addActionListener(e -> this.clearContent(redoButton, undoButton, editor));
        undoButton.addActionListener(e -> this.undoLastSearch(redoButton, undoButton, editor));
        redoButton.addActionListener(e -> this.redoLastSearch(redoButton, undoButton, editor));
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(final @NotNull DocumentEvent e) {
                // 获取新旧片段并预处理
                final CharSequence oldText = e.getOldFragment();
                final CharSequence newText = e.getNewFragment();
                // 内容无变化时直接跳过
                if (CharSequence.compare(oldText, newText) == 0) {
                    return;
                }
                // 内容无变化时直接跳过 - 忽略空白差异
                if (StrUtil.emptyIfNull(oldText).stripTrailing().trim().equals(StrUtil.emptyIfNull(newText).stripTrailing().trim())) {
                    return;
                }
                // 文档内容
                final String text = StrUtil.emptyIfNull(e.getDocument().getText());
                // 根据文档内容调整清空按钮的状态
                clearButton.setEnabled(!StrUtil.isEmpty(text));
                // 自动识别路径类型（Web或本地路径）、Jwt、Any并将其转换为格式化JSON，回写到编辑器
                MainPanel.this.OptPath(text, editor, e, this);
            }
        });
        // 上下文菜单
        editor.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(final MouseEvent e) {
                if (e.isPopupTrigger()) MainPanel.this.showEditorPopupMenu(redoButton, undoButton, editor, e);
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
                MainPanel.this.optJson(redoButton, undoButton, editor.getEditor(), new JsonFormatter());
            }
        }.registerCustomShortcutSet(
                new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("ReformatCode")),
                editor.getComponent()
        );
        // 搜索框快捷事件
        new AnAction() {
            @Override
            public void actionPerformed(final @NotNull AnActionEvent e) {
                MainPanel.this.performSearch(searchField, redoButton, undoButton, editor);
            }
        }.registerCustomShortcutSet(
                new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)),
                searchField
        );
    }

    /**
     * 自动识别路径类型（Web或本地路径）、Jwt、Any并将其转换为格式化JSON，回写到编辑器<br/>
     * 处理过程采用异步方式以避免阻塞UI线程，包含完整的异常处理和用户反馈
     * @param text     当前编辑器中的原始文本内容
     * @param editor   目标编辑器组件，用于回写处理结果
     * @param e        文档变更事件对象，用于操作关联的文档
     * @param listener 文档监听器，处理过程中需要临时解除绑定避免循环触发
     */
    private void OptPath(final String text,
                         final EditorTextField editor,
                         final @NotNull DocumentEvent e,
                         final @NotNull DocumentListener listener) {
        // 清空监听，防止重复执行
        e.getDocument().removeDocumentListener(listener);
        try {
            // 先进行路径解析，如不成功则再执行JWT解析，最后进行任意文件解析。都不成功则略过
            PathParser.convert(text)
                    // 路径 -> JWT
                    .thenCompose(pathResult -> JSON.isValid(pathResult) ? CompletableFuture.completedFuture(pathResult) : JwtParser.convert(text))
                    // JWT -> Any
                    .thenCompose(jwtResult -> JSON.isValid(jwtResult) ? CompletableFuture.completedFuture(jwtResult) : AnyParser.convert(text))
                    // 统一结果处理
                    .thenAccept(processedText -> ApplicationManager.getApplication().invokeLater(() -> {
                        if (JSON.isValid(processedText)) {
                            this.originalJson.set("");
                            editor.setText(processedText);
                        }
                    }));
        } finally {
            // 转换完成后，重新添加监听
            e.getDocument().addDocumentListener(listener);
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
        if (!operation.isValid(document.getText())) return;
        WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> {
            // 储存撤销历史
            this.undoStack.push(document.getText());
            // 将操作结果写回编辑器
            document.setText(StringUtil.convertLineSeparators(operation.process(document.getText())));
            // 更新按钮可用状态
            this.updateButtons(undoButton, redoButton);
        });
    }

    /**
     * 添加打开JSON文件操作
     * @param group  默认操作组
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
                MainPanel.this.handleFileOpen(editor);
            }
        });
    }

    /**
     * 添加差异操作
     * @param group  组
     * @param editor 编辑
     */
    private void addDiffAction(final DefaultActionGroup group, final EditorTextField editor) {
        group.add(new AnAction(
                BUNDLE.getString("menu.diff.viewer"),
                BUNDLE.getString("menu.diff.viewer.desc"),
                AllIcons.Actions.Diff
        ) {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                MainPanel.this.showDiffViewer(editor);
            }
        });
    }

    /**
     * 处理文件打开操作
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
                                    this.originalJson.set(text);
                                    // 清空撤销、重做记录
                                    this.undoStack.clear();
                                    this.redoStack.clear();
                                });
                                Notifier.notifyInfo("%s%s".formatted(BUNDLE.getString("file.load.success"), virtualFile.getPath()), editor.getProject());
                            });
                        } else {
                            ApplicationManager.getApplication().invokeLater(() ->
                                    Notifier.notifyError(BUNDLE.getString("file.load.failed"), editor.getProject())
                            );
                        }
                    } catch (final Exception ignored) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                Notifier.notifyError(BUNDLE.getString("file.load.failed"), editor.getProject())
                        );
                    }
                });
    }

    /**
     * 显示差异查看器
     * @param editor 编辑
     */
    private void showDiffViewer(final EditorTextField editor) {
        if (Objects.isNull(editor) || Objects.isNull(editor.getProject())) return;
        final DiffContentFactory factory = DiffContentFactory.getInstance();
        WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> DiffManager.getInstance().showDiff(editor.getProject(),
                new SimpleDiffRequest(
                        BUNDLE.getString("menu.diff.viewer"),
                        factory.createEditable(editor.getProject(), editor.getDocument().getText(), null),
                        factory.createEditable(editor.getProject(), Clipboard.get(), null), "", ""
                )
        ));
    }
}