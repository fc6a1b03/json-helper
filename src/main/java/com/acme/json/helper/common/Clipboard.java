package com.acme.json.helper.common;

import cn.hutool.core.convert.Convert;
import com.intellij.openapi.ide.CopyPasteManager;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

/**
 * 剪贴板
 * @author 拒绝者
 * @date 2025-01-25
 */
public class Clipboard {
    /**
     * 复制
     * @param content 内容
     */
    public static void copy(final String content) {
        CopyPasteManager.getInstance().setContents(new StringSelection(content));
    }

    /**
     * 获取粘贴板内容
     *
     * @return {@link String }
     */
    public static String get() {
        return Convert.toStr(CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor), "");
    }
}