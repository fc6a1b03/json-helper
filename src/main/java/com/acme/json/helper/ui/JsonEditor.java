package com.acme.json.helper.ui;

import com.intellij.json.JsonFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

/**
 * JSON编辑器
 * @author 拒绝者
 * @date 2025-01-19
 */
public class JsonEditor {
    /**
     * 创建JSON编辑器
     * @param project 项目
     * @param number  页签号数
     * @return {@link EditorTextField}
     */
    public EditorTextField create(final Project project, final int number) {
        return getEditorTextField(project, PsiDocumentManager.getInstance(project).getDocument(
                PsiFileFactory.getInstance(project).createFileFromText("dummy_%d.json".formatted(number), JsonFileType.INSTANCE, "")
        ));
    }

    /**
     * 获取编辑器文本字段
     * @param project  项目
     * @param document 文件
     * @return {@link EditorTextField }
     */
    private EditorTextField getEditorTextField(final Project project, final Document document) {
        return new EditorTextField(document, project, JsonFileType.INSTANCE, Boolean.FALSE, Boolean.FALSE) {
            @Override
            protected @NotNull EditorEx createEditor() {
                return configureEditor(project, super.createEditor());
            }
        };
    }

    /**
     * 编辑器设置
     * @param project 项目
     * @param editor  编辑
     */
    private EditorEx configureEditor(final Project project, final EditorEx editor) {
        // 基础编辑器配置
        editor.setCaretVisible(Boolean.TRUE);
        editor.setVerticalScrollbarVisible(Boolean.TRUE);
        editor.setHorizontalScrollbarVisible(Boolean.TRUE);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        // 折叠功能配置
        final FoldingModelEx foldingModel = editor.getFoldingModel();
        foldingModel.setFoldingEnabled(Boolean.TRUE);
        // 设置菜单和交互
        editor.setContextMenuGroupId(IdeActions.GROUP_EDITOR_POPUP);
        // 编辑器设置统一配置
        final EditorSettings settings = editor.getSettings();
        final EditorSettingsExternalizable externalSettings = EditorSettingsExternalizable.getInstance();
        settings.setUseSoftWraps(Boolean.TRUE);
        settings.setVirtualSpace(Boolean.FALSE);
        settings.setLineNumbersShown(Boolean.TRUE);
        settings.setShowIntentionBulb(Boolean.TRUE);
        settings.setIndentGuidesShown(Boolean.TRUE);
        settings.setFoldingOutlineShown(Boolean.TRUE);
        settings.setLineMarkerAreaShown(Boolean.TRUE);
        settings.setAutoCodeFoldingEnabled(Boolean.TRUE);
        settings.setAllowSingleLogicalLineFolding(Boolean.TRUE);
        settings.setBlockCursor(externalSettings.isBlockCursor());
        settings.setCaretBlinkPeriod(externalSettings.getBlinkPeriod());
        // 高亮和配色方案
        editor.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme());
        editor.setHighlighter(
                EditorHighlighterFactory.getInstance()
                        .createEditorHighlighter(project, JsonFileType.INSTANCE)
        );
        return editor;
    }
}