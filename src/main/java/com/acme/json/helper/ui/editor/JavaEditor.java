package com.acme.json.helper.ui.editor;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.EditorTextField;

/**
 * JAVA编辑器
 * @author 拒绝者
 * @date 2025-01-19
 */
public final class JavaEditor implements Editor {
    /**
     * 语言类型
     */
    private static final JavaFileType TYPE = JavaFileType.INSTANCE;

    /**
     * 创建JSON编辑器
     * @param project 项目
     * @return {@link EditorTextField}
     */
    public EditorTextField create(final Project project) {
        return getEditorTextField(TYPE, project, PsiDocumentManager.getInstance(project).getDocument(
                PsiFileFactory.getInstance(project).createFileFromText("Dummy.java", TYPE, "")
        ));
    }
}