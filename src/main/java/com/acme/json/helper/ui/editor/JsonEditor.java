package com.acme.json.helper.ui.editor;

import com.intellij.json.JsonFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.EditorTextField;

/**
 * JSON编辑器
 * @author 拒绝者
 * @date 2025-01-19
 */
public final class JsonEditor implements Editor {
    /** 语言类型 */
    private static final JsonFileType TYPE = JsonFileType.INSTANCE;

    /**
     * 创建JSON编辑器
     * @param project 项目
     * @param number  页签号数
     * @return {@link EditorTextField}
     */
    @Override
    public EditorTextField create(final Project project, final int number) {
        return getEditorTextField(TYPE, project, PsiDocumentManager.getInstance(project).getDocument(
                PsiFileFactory.getInstance(project).createFileFromText("Dummy_%d.json".formatted(number), TYPE, "")
        ));
    }
}