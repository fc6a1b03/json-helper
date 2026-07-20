package com.acme.json.helper.core.rainbow;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.List;

/**
 * 彩虹变量调色板：按变量名为其分配稳定、高区分度的前景色。
 * <p>每种颜色均提供浅色主题（Light）与深色主题（Dark，如 Darcula）双变体，
 * 由 {@link JBColor} 按当前主题自动切换。</p>
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
public final class RainbowVariablePalette {
    /**
     * 静态不可变调色板：8 组高区分度颜色（Light/Dark 双变体）
     */
    private static final List<JBColor> COLORS = List.of(
            new JBColor(new Color(0xC7254E), new Color(0xFF6B81)),
            new JBColor(new Color(0xB36B00), new Color(0xFFA94D)),
            new JBColor(new Color(0x8A6D00), new Color(0xFFD43B)),
            new JBColor(new Color(0x1E7E34), new Color(0x69DB7C)),
            new JBColor(new Color(0x0C7285), new Color(0x66D9E8)),
            new JBColor(new Color(0x1C5CD6), new Color(0x74C0FC)),
            new JBColor(new Color(0x7B2CBF), new Color(0xB197FC)),
            new JBColor(new Color(0xB030B0), new Color(0xF783AC))
    );

    private RainbowVariablePalette() {
    }

    /**
     * 按变量名稳定取色：同名变量在全文件范围内始终同色。
     *
     * @param name 变量名
     * @return 该变量名对应的前景色（随主题切换 Light/Dark 变体）
     */
    @NotNull
    public static JBColor forName(@NotNull final String name) {
        return COLORS.get(Math.floorMod(name.hashCode(), COLORS.size()));
    }
}
