package com.acme.prism.core.rainbow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 颜色字面量解析器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
class ColorLiteralParserTest {

    @Test
    @DisplayName("HEX：3 位短格式逐位展开，alpha 默认 255")
    void parsesShortHex() {
        final var matches = ColorLiteralParser.scan("#f00", 10);
        assertAll(
                () -> assertEquals(1, matches.size(), "应命中 1 个颜色"),
                () -> assertEquals(new Color(255, 0, 0), matches.getFirst().color(), "#f00 应展开为纯红"),
                () -> assertEquals(0, matches.getFirst().start(), "起始偏移应为 0"),
                () -> assertEquals(4, matches.getFirst().end(), "结束偏移应覆盖整个字面量")
        );
    }

    @Test
    @DisplayName("HEX：4 位短格式按 RGBA 展开")
    void parsesShortHexWithAlpha() {
        final var matches = ColorLiteralParser.scan("#f008", 10);
        assertAll(
                () -> assertEquals(1, matches.size(), "应命中 1 个颜色"),
                () -> assertEquals(new Color(255, 0, 0, 0x88), matches.getFirst().color(),
                        "#f008 末位 alpha 应翻倍展开为 0x88")
        );
    }

    @Test
    @DisplayName("HEX：6 位标准格式，alpha 默认 255")
    void parsesFullHex() {
        final var matches = ColorLiteralParser.scan("#ff0000", 10);
        assertAll(
                () -> assertEquals(1, matches.size(), "应命中 1 个颜色"),
                () -> assertEquals(new Color(255, 0, 0), matches.getFirst().color(), "#ff0000 应为不透明纯红")
        );
    }

    @Test
    @DisplayName("HEX：8 位格式末两位为 alpha")
    void parsesFullHexWithAlpha() {
        final var matches = ColorLiteralParser.scan("#ff000080", 10);
        assertAll(
                () -> assertEquals(1, matches.size(), "应命中 1 个颜色"),
                () -> assertEquals(new Color(255, 0, 0, 0x80), matches.getFirst().color(), "#ff000080 alpha 应为 0x80")
        );
    }

    @Test
    @DisplayName("函数式：rgb 三个整数分量，alpha 默认 255")
    void parsesRgbFunction() {
        final var matches = ColorLiteralParser.scan("rgb(255, 0, 0)", 10);
        assertAll(
                () -> assertEquals(1, matches.size(), "应命中 1 个颜色"),
                () -> assertEquals(new Color(255, 0, 0), matches.getFirst().color(), "rgb(255, 0, 0) 应为不透明纯红")
        );
    }

    @Test
    @DisplayName("函数式：rgba 小数 alpha 按 0~1 乘 255 归一")
    void parsesRgbaWithDecimalAlpha() {
        final var matches = ColorLiteralParser.scan("rgba(255, 0, 0, 0.5)", 10);
        assertAll(
                () -> assertEquals(1, matches.size(), "应命中 1 个颜色"),
                () -> assertEquals(new Color(255, 0, 0, 128), matches.getFirst().color(), "0.5 应归一为 128")
        );
    }

    @Test
    @DisplayName("函数式：rgba 百分数 alpha 按 0~100 换算")
    void parsesRgbaWithPercentAlpha() {
        final var matches = ColorLiteralParser.scan("rgba(255, 0, 0, 50%)", 10);
        assertAll(
                () -> assertEquals(1, matches.size(), "应命中 1 个颜色"),
                () -> assertEquals(new Color(255, 0, 0, 128), matches.getFirst().color(), "50% 应换算为 128")
        );
    }

    @Test
    @DisplayName("函数式：rgba 整数 alpha 按 0~255 直取")
    void parsesRgbaWithIntegerAlpha() {
        final var matches = ColorLiteralParser.scan("rgba(255, 0, 0, 128)", 10);
        assertAll(
                () -> assertEquals(1, matches.size(), "应命中 1 个颜色"),
                () -> assertEquals(new Color(255, 0, 0, 128), matches.getFirst().color(), "整数 alpha 应直取 128")
        );
    }

    @Test
    @DisplayName("越界：分量或 alpha 越界的命中被丢弃")
    void dropsOutOfRangeComponents() {
        assertAll(
                () -> assertTrue(ColorLiteralParser.scan("rgb(256, 0, 0)", 10).isEmpty(), "分量 256 应丢弃"),
                () -> assertTrue(ColorLiteralParser.scan("rgba(0, 0, 0, 300)", 10).isEmpty(), "整数 alpha 300 应丢弃"),
                () -> assertTrue(ColorLiteralParser.scan("rgba(0, 0, 0, 1.5)", 10).isEmpty(), "小数 alpha 1.5 应丢弃"),
                () -> assertTrue(ColorLiteralParser.scan("rgba(0, 0, 0, 101%)", 10).isEmpty(), "百分数 alpha 101% 应丢弃")
        );
    }

    @Test
    @DisplayName("词边界：abc#ff00 命中且偏移正确，#ff0g 与 5/7 位序列不命中")
    void respectsWordBoundaries() {
        final var matches = ColorLiteralParser.scan("abc#ff00", 10);
        assertAll(
                () -> assertEquals(1, matches.size(), "前置普通字符不影响命中"),
                () -> assertEquals(3, matches.getFirst().start(), "字面量应从 # 处开始"),
                () -> assertEquals(new Color(255, 255, 0, 0), matches.getFirst().color(),
                        "#ff00 为 4 位 RGBA，应为透明黄"),
                () -> assertTrue(ColorLiteralParser.scan("#ff0g", 10).isEmpty(), "尾随单词字符 #ff0g 不应命中"),
                () -> assertTrue(ColorLiteralParser.scan("#ff00a", 10).isEmpty(), "5 位序列不应部分命中"),
                () -> assertTrue(ColorLiteralParser.scan("#ff00aa5", 10).isEmpty(), "7 位序列不应部分命中")
        );
    }

    @Test
    @DisplayName("截断：命中数超出 maxMatches 时按序截断")
    void truncatesAtMaxMatches() {
        final var matches = ColorLiteralParser.scan("#f00 #0f0 #00f", 2);
        assertAll(
                () -> assertEquals(2, matches.size(), "应截断为 2 个命中"),
                () -> assertEquals(new Color(255, 0, 0), matches.get(0).color(), "第一个应为 #f00"),
                () -> assertEquals(new Color(0, 255, 0), matches.get(1).color(), "第二个应为 #0f0")
        );
    }

    @Test
    @DisplayName("边界：null、空串与非正 maxMatches 返回空列表")
    void returnsEmptyForInvalidInput() {
        assertAll(
                () -> assertTrue(ColorLiteralParser.scan(null, 10).isEmpty(), "null 文本应返回空"),
                () -> assertTrue(ColorLiteralParser.scan("", 10).isEmpty(), "空文本应返回空"),
                () -> assertTrue(ColorLiteralParser.scan("#f00", 0).isEmpty(), "maxMatches 为 0 应返回空")
        );
    }

    @Test
    @DisplayName("混合文本：字符串与注释中的字面量均识别，且按偏移量升序")
    void scansMixedTextInOrder() {
        final var matches = ColorLiteralParser.scan("color = \"#00ff00\"; // rgb(0,0,255)", 10);
        assertAll(
                () -> assertEquals(2, matches.size(), "应命中 2 个颜色"),
                () -> assertEquals(new Color(0, 255, 0), matches.get(0).color(), "第一个应为字符串中的 HEX"),
                () -> assertEquals(new Color(0, 0, 255), matches.get(1).color(), "第二个应为注释中的 rgb 函数"),
                () -> assertTrue(matches.get(0).start() < matches.get(1).start(), "结果应按偏移量升序")
        );
    }
}
