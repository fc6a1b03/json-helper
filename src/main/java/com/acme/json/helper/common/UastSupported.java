package com.acme.json.helper.common;

import cn.hutool.core.lang.Opt;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.uast.UastLanguagePlugin;

import java.util.Objects;

/**
 * 检查UAST语言支持
 *
 * @author 拒绝者
 * @date 2025-01-28
 */
public class UastSupported {
    /**
     * 检查UAST语言支持
     *
     * @param psiFile PSI文件
     * @return boolean
     */
    public static boolean of(final PsiFile psiFile) {
        return Opt.ofNullable(psiFile)
                .map(file -> UastLanguagePlugin.Companion.getInstances()
                        .stream()
                        .anyMatch(plugin -> plugin.isFileSupported(file.getName())))
                .orElse(Boolean.FALSE);
    }

    /**
     * 检查是否存在有效的类上下文
     *
     * @param editor 编辑器
     * @param psiFile PSI文件
     * @return boolean
     */
    public static boolean hasValidClassContext(final Editor editor, final PsiFile psiFile) {
        return Objects.nonNull(editor) && Objects.nonNull(psiFile) && Objects.nonNull(locatePsiClass(editor, psiFile));
    }

    /**
     * 定位当前光标所在的PSI类
     *
     * @param editor 编辑器
     * @param psiFile PSI文件
     * @return {@link PsiClass }
     */
    public static PsiClass locatePsiClass(final Editor editor, final PsiFile psiFile) {
        return PsiTreeUtil.getParentOfType(
                psiFile.findElementAt(editor.getCaretModel().getOffset()),
                PsiClass.class
        );
    }
}