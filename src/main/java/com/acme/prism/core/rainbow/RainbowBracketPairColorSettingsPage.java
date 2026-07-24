package com.acme.prism.core.rainbow;

import com.intellij.icons.AllIcons;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * 彩虹括号配对高亮的颜色设置页。
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
public final class RainbowBracketPairColorSettingsPage implements ColorSettingsPage {
    /**
     * 语言资源
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.PrismBundle");

    /**
     * 演示文本，使用 XML 标签标记需要展示颜色的括号
     */
    private static final String DEMO_TEXT = """
            <brace>{</brace>
                "name": "Rainbow Brackets",
                "features": <bracket>[</bracket>
                    "format",
                    "convert",
                    "tree"
                <bracket>]</bracket>,
                "nested": <brace>{</brace>
                    "enabled": true,
                    "items": <bracket>[</bracket> <paren>(1)</paren>, <paren>(2)</paren>, <paren>(3)</paren> <bracket>]</bracket>
                <brace>}</brace>
            <brace>}</brace>
            """;

    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
            new AttributesDescriptor(BUNDLE.getString("rainbow.bracket.pair.color.brace"), RainbowBracketPairColors.BRACE),
            new AttributesDescriptor(BUNDLE.getString("rainbow.bracket.pair.color.bracket"), RainbowBracketPairColors.BRACKET),
            new AttributesDescriptor(BUNDLE.getString("rainbow.bracket.pair.color.parenthesis"), RainbowBracketPairColors.PARENTHESIS)
    };

    private static final Map<String, TextAttributesKey> TAG_TO_DESCRIPTOR = new HashMap<>();

    static {
        TAG_TO_DESCRIPTOR.put("brace", RainbowBracketPairColors.BRACE);
        TAG_TO_DESCRIPTOR.put("bracket", RainbowBracketPairColors.BRACKET);
        TAG_TO_DESCRIPTOR.put("paren", RainbowBracketPairColors.PARENTHESIS);
    }

    @Override
    public @Nullable Icon getIcon() {
        return AllIcons.Actions.IntentionBulb;
    }

    @Override
    public @Nullable SyntaxHighlighter getHighlighter() {
        return SyntaxHighlighterFactory.getSyntaxHighlighter(JsonFileType.INSTANCE, null, null);
    }

    @Override
    public @NotNull @NonNls String getDemoText() {
        return DEMO_TEXT;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return TAG_TO_DESCRIPTOR;
    }

    @Override
    public @NotNull AttributesDescriptor[] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public @NotNull ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return BUNDLE.getString("rainbow.bracket.pair.color.page");
    }
}
