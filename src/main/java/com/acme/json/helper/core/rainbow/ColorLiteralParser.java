package com.acme.json.helper.core.rainbow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 颜色字面量解析器。
 * <p>以纯文本方式识别 HEX（#RGB/#RGBA/#RRGGBB/#RRGGBBAA）与 rgb()/rgba() 函数式颜色，
 * 与具体语言无关（字符串、注释中出现同样识别）。无 IDE 依赖，可独立单元测试。</p>
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
public final class ColorLiteralParser {
    /**
     * HEX 颜色：# 前缀 + 3/4/6/8 位十六进制（长者优先，词边界收尾防止误伤，如 #ff0g、5/7 位序列均不命中）
     */
    private static final Pattern HEX_PATTERN = Pattern.compile(
            "#(?<hex>[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})\\b");
    /**
     * rgb()/rgba() 函数：分量 0~255 整数；可选 alpha 支持 0~1 小数、0~100 百分数、0~255 整数
     */
    private static final Pattern RGB_FUNCTION_PATTERN = Pattern.compile(
            "\\brgba?\\s*\\(\\s*(?<r>\\d{1,3})\\s*,\\s*(?<g>\\d{1,3})\\s*,\\s*(?<b>\\d{1,3})"
                    + "\\s*(?:,\\s*(?<a>\\d+(?:\\.\\d+)?|\\.\\d+)\\s*(?<pct>%)?)?\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    /**
     * 合并模式：单遍扫描同时命中两类字面量，保证结果按文本偏移量有序
     */
    private static final Pattern COMBINED_PATTERN = Pattern.compile(
            HEX_PATTERN.pattern() + "|" + RGB_FUNCTION_PATTERN.pattern(), Pattern.CASE_INSENSITIVE);
    /**
     * 分量上限（rgb 三通道取值 0~255）
     */
    private static final int MAX_COMPONENT = 255;
    /**
     * 百分数 alpha 上限（0~100%）
     */
    private static final double MAX_PERCENT = 100.0;

    private ColorLiteralParser() {
    }

    /**
     * 全文扫描颜色字面量。
     *
     * @param text       待扫描文本，null 或空文本直接返回空列表
     * @param maxMatches 命中数上限，超出后截断返回（防止极端文本产生过多结果）
     * @return 命中列表，按起始偏移量升序
     */
    @NotNull
    public static List<ColorMatch> scan(@Nullable final CharSequence text, final int maxMatches) {
        if (Objects.isNull(text) || text.isEmpty() || maxMatches <= 0) {
            return List.of();
        }
        final var matches = new ArrayList<ColorMatch>();
        final var matcher = COMBINED_PATTERN.matcher(text);
        while (matcher.find() && matches.size() < maxMatches) {
            final var hex = matcher.group("hex");
            final var color = Objects.nonNull(hex) ? parseHexColor(hex) : parseFunctionColor(matcher);
            if (Objects.nonNull(color)) {
                matches.add(new ColorMatch(matcher.start(), matcher.end(), color));
            }
        }
        return matches;
    }

    /**
     * 解析 HEX 颜色：3/4 位逐字符展开为 6/8 位；alpha 位于末两位，缺省为 255。
     */
    @Nullable
    private static Color parseHexColor(@NotNull final String hex) {
        final var expanded = switch (hex.length()) {
            case 3, 4 -> expandShortHex(hex);
            case 6, 8 -> hex;
            default -> null;
        };
        if (Objects.isNull(expanded)) {
            return null;
        }
        final var red = Integer.parseInt(expanded.substring(0, 2), 16);
        final var green = Integer.parseInt(expanded.substring(2, 4), 16);
        final var blue = Integer.parseInt(expanded.substring(4, 6), 16);
        final var alpha = expanded.length() == 8 ? Integer.parseInt(expanded.substring(6, 8), 16) : MAX_COMPONENT;
        return new Color(red, green, blue, alpha);
    }

    /**
     * 将 3/4 位短 HEX 逐字符翻倍展开（如 f08 -> ff0088）。
     */
    @NotNull
    private static String expandShortHex(@NotNull final String hex) {
        final var expanded = new StringBuilder(hex.length() * 2);
        for (var i = 0; i < hex.length(); i++) {
            expanded.append(hex.charAt(i)).append(hex.charAt(i));
        }
        return expanded.toString();
    }

    /**
     * 解析 rgb()/rgba() 函数：分量越界或 alpha 非法时丢弃该命中（返回 null）。
     */
    @Nullable
    private static Color parseFunctionColor(@NotNull final Matcher matcher) {
        final var red = parseComponent(matcher.group("r"));
        final var green = parseComponent(matcher.group("g"));
        final var blue = parseComponent(matcher.group("b"));
        if (red < 0 || green < 0 || blue < 0) {
            return null;
        }
        final var alpha = parseAlpha(matcher.group("a"), Objects.nonNull(matcher.group("pct")));
        if (alpha < 0) {
            return null;
        }
        return new Color(red, green, blue, alpha);
    }

    /**
     * 解析单个颜色分量：合法范围 0~255，越界返回 -1。
     */
    private static int parseComponent(@NotNull final String text) {
        final var value = Integer.parseInt(text);
        return value <= MAX_COMPONENT ? value : -1;
    }

    /**
     * 归一化 alpha：
     * <ul>
     *     <li>缺省：255（不透明）</li>
     *     <li>百分数：0~100 换算到 0~255</li>
     *     <li>小数（含小数点）：0~1 乘 255</li>
     *     <li>整数：0~255 直取</li>
     * </ul>
     * 任一形式越界返回 -1。
     */
    private static int parseAlpha(@Nullable final String text, final boolean percent) {
        if (Objects.isNull(text)) {
            return MAX_COMPONENT;
        }
        if (percent) {
            final var value = Double.parseDouble(text);
            if (value < 0 || value > MAX_PERCENT) {
                return -1;
            }
            return (int) Math.round(value / MAX_PERCENT * MAX_COMPONENT);
        }
        if (text.indexOf('.') >= 0) {
            final var ratio = Double.parseDouble(text);
            if (ratio < 0 || ratio > 1) {
                return -1;
            }
            return (int) Math.round(ratio * MAX_COMPONENT);
        }
        final var value = Integer.parseInt(text);
        return value <= MAX_COMPONENT ? value : -1;
    }

    /**
     * 颜色命中结果。
     *
     * @param start 字面量起始偏移量（含 # 或函数名）
     * @param end   字面量结束偏移量（不含）
     * @param color 解析出的颜色
     */
    public record ColorMatch(int start, int end, Color color) {
    }
}
