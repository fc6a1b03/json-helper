package com.acme.json.helper.ui;

import com.acme.json.helper.core.*;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
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
     * @return {@link EditorTextField}
     */
    public EditorTextField create(final Project project) {
        // 创建带有完整 JSON 支持的编辑器
        final EditorTextField editor = getEditorTextField(project, EditorFactory.getInstance().createDocument(""));
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
        final FileType jsonFileType = FileTypeManager.getInstance().getFileTypeByExtension("json");
        return new EditorTextField(document, project, jsonFileType, Boolean.FALSE, Boolean.FALSE) {
            @Override
            protected @NotNull EditorEx createEditor() {
                final EditorEx editor = super.createEditor();
                // 编辑器高级设置
                editorSettings(project, editor, jsonFileType);
                // 添加JSON格式化支持
                jsonFormatAction(editor);
                return editor;
            }
        };
    }

    /**
     * 编辑器设置
     * @param project      项目
     * @param editor       编辑
     * @param jsonFileType json文件类型
     */
    private void editorSettings(final Project project, final EditorEx editor, final FileType jsonFileType) {
        // 编辑器设置
        editor.setCaretVisible(Boolean.TRUE);
        editor.setVerticalScrollbarVisible(Boolean.TRUE);
        editor.setHorizontalScrollbarVisible(Boolean.TRUE);
        editor.getFoldingModel().setFoldingEnabled(Boolean.TRUE);
        editor.setContextMenuGroupId(IdeActions.GROUP_EDITOR_POPUP);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        // 设置配色方案
        editor.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme());
        editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, jsonFileType));
        // 设置字体
        editor.getColorsScheme().setEditorFontName(EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName());
        editor.getColorsScheme().setEditorFontSize(EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize());
        // 编辑器详细设置
        final EditorSettings settings = editor.getSettings();
        // 启用文本编辑器基础功能
        settings.setBlockCursor(Boolean.TRUE);
        settings.setUseSoftWraps(Boolean.TRUE);
        settings.setVirtualSpace(Boolean.TRUE);
        settings.setLineNumbersShown(Boolean.TRUE);
        settings.setShowIntentionBulb(Boolean.TRUE);
        settings.setIndentGuidesShown(Boolean.TRUE);
        settings.setFoldingOutlineShown(Boolean.TRUE);
        settings.setLineMarkerAreaShown(Boolean.TRUE);
        settings.setAutoCodeFoldingEnabled(Boolean.TRUE);
        // 设置光标样式
        settings.setBlockCursor(EditorSettingsExternalizable.getInstance().isBlockCursor());
        settings.setCaretBlinkPeriod(EditorSettingsExternalizable.getInstance().getBlinkPeriod());
    }

    /**
     * JSON格式化动作
     * @param editor 编辑
     */
    private void jsonFormatAction(final EditorEx editor) {
        // 注册格式化快捷键
        new AnAction() {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                optJson(editor, new JsonFormatter());
                final Project project = editor.getProject();
                if (Objects.nonNull(project)) {
                    CodeFoldingManager.getInstance(project).updateFoldRegions(editor);
                }
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
        // 添加分隔符
        group.addSeparator();
        // 使用 IdeActions 中预定义的操作ID
        group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_CUT));
        group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));
        group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_PASTE));
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
        WriteCommandAction.runWriteCommandAction(project, () -> {
            // 更新编辑器内容
            document.setText(StringUtil.convertLineSeparators(operation.process(document.getText())));
            // 触发重新解析
            PsiDocumentManager.getInstance(project).commitDocument(document);
            CodeFoldingManager.getInstance(project).updateFoldRegions(editor);
        });
    }
}