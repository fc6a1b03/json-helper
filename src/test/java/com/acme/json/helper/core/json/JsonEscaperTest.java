package com.acme.json.helper.core.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON转义器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class JsonEscaperTest {

    private final JsonEscaper escaper = new JsonEscaper();

    @Test
    @DisplayName("正常：JSON 文本被整体加引号并转义")
    void escapesJsonText() {
        assertEquals("\"{\\\"a\\\":1}\"", escaper.process("{\"a\":1}"),
                "整个字符串应作为 JSON 字符串值序列化，整体加引号且内部引号转义");
    }

    @Test
    @DisplayName("正常：普通文本被整体加引号")
    void escapesPlainText() {
        assertEquals("\"hello world\"", escaper.process("hello world"), "普通文本应整体加引号");
    }

    @Test
    @DisplayName("边界：空字符串被序列化为两个引号")
    void escapesEmptyString() {
        assertEquals("\"\"", escaper.process(""), "空串应序列化为 JSON 空字符串字面量");
    }

    @Test
    @DisplayName("边界：换行与制表符被转义为字面序列")
    void escapesControlCharacters() {
        assertEquals("\"a\\nb\\tc\"", escaper.process("a\nb\tc"), "控制字符应转义为 \\n、\\t 字面序列");
    }

    @Test
    @DisplayName("边界：中文不转义、内嵌引号被转义")
    void escapesChineseAndQuotes() {
        assertEquals("\"中文\\\"引号\\\"\"", escaper.process("中文\"引号\""),
                "fastjson2 默认不转义非 ASCII 字符，仅转义引号");
    }

    @Test
    @DisplayName("边界：深层嵌套 JSON 文本被整体转义为单行")
    void escapesDeeplyNestedText() {
        final String result = escaper.process(deepJson(20));
        assertAll(
                () -> assertTrue(result.startsWith("\""), "转义结果应以引号开头"),
                () -> assertTrue(result.endsWith("\""), "转义结果应以引号结尾"),
                () -> assertFalse(result.contains("\n"), "转义结果应为单行"),
                () -> assertTrue(result.contains("\\\"v\\\""), "内部引号应被转义")
        );
    }

    @Test
    @DisplayName("异常：null 输入不抛异常并序列化为 null 字面量")
    void escapesNullInput() {
        assertAll(
                () -> assertDoesNotThrow(() -> escaper.process((String) null), "null 输入不应抛异常"),
                () -> assertEquals("null", escaper.process((String) null), "null 应序列化为 \"null\" 字面量")
        );
    }

    private static String deepJson(final int depth) {
        final StringBuilder sb = new StringBuilder("{\"v\":");
        for (int i = 0; i < depth; i++) {
            sb.append("{\"n\":");
        }
        sb.append("1");
        for (int i = 0; i < depth; i++) {
            sb.append("}");
        }
        return sb.append("}").toString();
    }
}
