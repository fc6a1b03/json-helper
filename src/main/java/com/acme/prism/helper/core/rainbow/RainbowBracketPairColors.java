package com.acme.prism.core.rainbow;

import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * 彩虹括号配对高亮使用的颜色键。
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
public final class RainbowBracketPairColors {
    private RainbowBracketPairColors() {
    }

    /**
     * 花括号 {} 颜色
     */
    public static final TextAttributesKey BRACE =
            TextAttributesKey.createTextAttributesKey("JSON_HELPER_RAINBOW_BRACE");

    /**
     * 方括号 [] 颜色
     */
    public static final TextAttributesKey BRACKET =
            TextAttributesKey.createTextAttributesKey("JSON_HELPER_RAINBOW_BRACKET");

    /**
     * 圆括号 () 颜色
     */
    public static final TextAttributesKey PARENTHESIS =
            TextAttributesKey.createTextAttributesKey("JSON_HELPER_RAINBOW_PARENTHESIS");
}
