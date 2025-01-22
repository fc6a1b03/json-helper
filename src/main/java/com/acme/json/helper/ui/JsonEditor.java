package com.acme.json.helper.ui;

import com.acme.json.helper.core.*;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * JSON编辑器
 * @author 拒绝者
 * @date 2025-01-19
 */
public class JsonEditor {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");

    /**
     * 创建JSON编辑器
     * @param project 项目
     * @param number  页签号数
     * @return {@link EditorTextField}
     */
    public EditorTextField create(final Project project, final int number) {
        // 创建带有JSON PSI文件的文档
        final PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("dummy_%d.json".formatted(number), JsonFileType.INSTANCE, "");
        // 创建带有完整 JSON 支持的编辑器
        final EditorTextField editor = getEditorTextField(project, PsiDocumentManager.getInstance(project).getDocument(psiFile));
        // 添加上下文菜单支持
        editor.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showEditorPopupMenu(editor, e);
                }
            }
        });
        return editor;
    }

    /**
     * 获取编辑器文本字段
     * @param project  项目
     * @param document 文件
     * @return {@link EditorTextField }
     */
    private @NotNull EditorTextField getEditorTextField(final Project project, final Document document) {
        return new EditorTextField(document, project, JsonFileType.INSTANCE, Boolean.FALSE, Boolean.FALSE) {
            @Override
            protected @NotNull EditorEx createEditor() {
                final EditorEx editor = super.createEditor();
                // 编辑器高级设置
                editorSetting(project, editor);
                // 添加JSON格式化支持
                editorAction(editor);
                return editor;
            }
        };
    }

    /**
     * 编辑器设置
     * @param project 项目
     * @param editor  编辑
     */
    private void editorSetting(final Project project, final EditorEx editor) {
        // 编辑器设置
        editor.setCaretVisible(Boolean.TRUE);
        editor.setVerticalScrollbarVisible(Boolean.TRUE);
        editor.setHorizontalScrollbarVisible(Boolean.TRUE);
        editor.getFoldingModel().setFoldingEnabled(Boolean.TRUE);
        editor.setContextMenuGroupId(IdeActions.GROUP_EDITOR_POPUP);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        editor.getSettings().setUseSoftWraps(Boolean.TRUE);
        editor.getSettings().setVirtualSpace(Boolean.FALSE);
        editor.getSettings().setLineNumbersShown(Boolean.TRUE);
        editor.getSettings().setShowIntentionBulb(Boolean.TRUE);
        editor.getSettings().setIndentGuidesShown(Boolean.TRUE);
        editor.getSettings().setFoldingOutlineShown(Boolean.TRUE);
        editor.getSettings().setLineMarkerAreaShown(Boolean.TRUE);
        editor.getSettings().setAutoCodeFoldingEnabled(Boolean.TRUE);
        editor.getSettings().setAllowSingleLogicalLineFolding(Boolean.TRUE);
        editor.getSettings().setBlockCursor(EditorSettingsExternalizable.getInstance().isBlockCursor());
        editor.getSettings().setCaretBlinkPeriod(EditorSettingsExternalizable.getInstance().getBlinkPeriod());
        editor.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme());
        editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, JsonFileType.INSTANCE));
    }

    /**
     * 编辑器动作
     * @param editor 编辑
     */
    private void editorAction(final EditorEx editor) {
        // 注册格式化快捷键
        new AnAction() {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                // 操作JSON
                optJson(editor, new JsonFormatter());
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("ReformatCode")), editor.getComponent());
    }

    /**
     * 显示编辑器上下文菜单
     * @param editor 编辑器
     * @param e      鼠标事件
     */
    private void showEditorPopupMenu(final EditorTextField editor, final MouseEvent e) {
        final DefaultActionGroup group = new DefaultActionGroup();
        // 添加格式化操作
        group.add(new AnAction(
                BUNDLE.getString("json.format.json"),
                BUNDLE.getString("json.format.json.desc"),
                AllIcons.Actions.Refresh
        ) {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                optJson(editor.getEditor(), new JsonFormatter());
            }
        });
        // 添加压缩操作
        group.add(new AnAction(
                BUNDLE.getString("json.compress.json"),
                BUNDLE.getString("json.compress.json.desc"),
                AllIcons.Actions.Collapseall
        ) {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                optJson(editor.getEditor(), new JsonCompressor());
            }
        });
        // 添加转义操作
        group.add(new AnAction(
                BUNDLE.getString("json.escaping.json"),
                BUNDLE.getString("json.escaping.json.desc"),
                AllIcons.Javaee.UpdateRunningApplication
        ) {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                optJson(editor.getEditor(), new JsonEscaper());
            }
        });
        // 添加去转义操作
        group.add(new AnAction(
                BUNDLE.getString("json.un.escaping.json"),
                BUNDLE.getString("json.un.escaping.json.desc"),
                AllIcons.Actions.SearchNewLine
        ) {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                optJson(editor.getEditor(), new JsonUnEscaper());
            }
        });
        group.addSeparator();
        group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_EDITOR_POPUP));
        // 显示菜单
        ActionManager.getInstance()
                .createActionPopupMenu("JsonEditorPopup", group)
                .getComponent()
                .show(editor.getComponent(), e.getX(), e.getY());
    }

    /**
     * 操作JSON
     * @param editor    编辑器
     * @param operation 操作
     */
    private void optJson(final Editor editor, final JsonOperation operation) {
        if (Objects.isNull(editor) || Objects.isNull(editor.getProject())) return;
        final Project project = editor.getProject();
        final Document document = editor.getDocument();
        if (StringUtil.isEmpty(document.getText())) return;
        WriteCommandAction.runWriteCommandAction(project, () -> document.setText(StringUtil.convertLineSeparators(operation.process(document.getText()))));
    }
}