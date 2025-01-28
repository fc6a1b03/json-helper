package com.acme.json.helper.common;

import cn.hutool.core.lang.Opt;
import com.intellij.psi.PsiFile;
import org.jetbrains.uast.UastLanguagePlugin;

/**
 * 检查UAST语言支持（Java文件检测）
 *
 * @author 拒绝者
 * @date 2025-01-28
 */
public class UastSupported {
    /**
     * 检查UAST语言支持（Java文件检测）
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
}