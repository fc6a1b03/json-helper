package com.acme.json.helper.common;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

/**
 * 代码格式化器
 *
 * @author 拒绝者
 * @date 2025-01-26
 */
public class JavaCodeFormatter {
    /**
     * 执行
     *
     * @param code 代码
     * @return {@link String }
     */
    public static String exec(final String code) {
        try {
            final IDocument doc = new Document(code);
            ToolFactory.createCodeFormatter(JavaCore.getOptions())
                    .format(
                            CodeFormatter.K_COMPILATION_UNIT,
                            code,
                            0,
                            code.length(),
                            0,
                            System.lineSeparator()
                    ).apply(doc);
            return doc.get();
        } catch (Exception e) {
            return code;
        }
    }
}