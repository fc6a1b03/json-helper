package com.acme.json.helper.ui.editor;

import com.acme.json.helper.ui.editor.enums.SupportedLanguages;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.EditorTextField;

/**
 * 编辑器工厂
 * @param fileName 文件名
 * @param language 编辑器语言
 * @author 拒绝者
 * @date 2025-04-22
 */
public record CustomizeEditorFactory(SupportedLanguages language, String fileName) implements Editor {
    /**
     * 编辑器工厂
     * @param language 语言
     * @param fileName 文件名
     */
    public CustomizeEditorFactory {
    }

    @Override
    public EditorTextField create(final Project project) {
        return getEditorTextField(language.getFileType(), project, PsiDocumentManager.getInstance(project).getDocument(
                PsiFileFactory.getInstance(project).createFileFromText(fileName, language.getFileType(), "")
        ));
    }
}