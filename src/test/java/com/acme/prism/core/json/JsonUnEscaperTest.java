package com.acme.prism.core.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON去转义器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class JsonUnEscaperTest {

    private final JsonUnEscaper unEscaper = new JsonUnEscaper();

    @Test
    @DisplayName("正常：转义的 JSON 对象字符串被去转义并格式化")
    void unEscapesEscapedObject() {
        final String result = unEscaper.process("\"{\\\"a\\\":1}\"");
        assertAll(
                () -> assertTrue(result.startsWith("{"), "去转义结果应为 JSON 对象"),
                () -> assertTrue(result.contains("\"a\":1"), "去转义结果应保留字段"),
                () -> assertTrue(result.contains("\n"), "去转义结果应被格式化为多行")
        );
    }

    @Test
    @DisplayName("正常：转义的嵌套 JSON 被去转义并格式化")
    void unEscapesNestedJson() {
        final String result = unEscaper.process("\"{\\\"a\\\":{\\\"b\\\":[1,2]}}\"");
        assertAll(
                () -> assertTrue(result.contains("\"b\":["), "嵌套数组字段应保留"),
                () -> assertTrue(result.contains("\n"), "结果应为多行格式化输出")
        );
    }

    @Test
    @DisplayName("正常：JSON 字符串字面量被去除首尾引号")
    void unEscapesPlainString() {
        assertAll(
                () -> assertEquals("hello", unEscaper.process("\"hello\""), "JSON 字符串字面量应去除首尾引号"),
                () -> assertEquals("123", unEscaper.process("123"), "数字文本解析为字符串后原样输出")
        );
    }

    @Test
    @DisplayName("正常：非字符串形式的 JSON 对象文本被直接格式化")
    void unEscapesRawJsonObject() {
        final String result = unEscaper.process("{\"a\":1}");
        assertAll(
                () -> assertTrue(result.contains("\"a\":1"), "原始 JSON 对象文本经解析后应保留字段"),
                () -> assertTrue(result.contains("\n"), "原始 JSON 对象文本应被格式化为多行")
        );
    }

    @Test
    @DisplayName("边界：空字符串解析为 null 并序列化为 null 字面量")
    void unEscapesEmptyString() {
        assertEquals("null", unEscaper.process(""), "空串经 fastjson2 解析为 null，格式化输出 \"null\"");
    }

    @Test
    @DisplayName("边界：null 输入不抛异常并输出 null 字面量")
    void unEscapesNullInput() {
        assertAll(
                () -> assertDoesNotThrow(() -> unEscaper.process((String) null), "null 输入不应抛异常"),
                () -> assertEquals("null", unEscaper.process((String) null), "null 应输出 \"null\" 字面量")
        );
    }

    @Test
    @DisplayName("边界：深层嵌套转义文本被去转义并格式化")
    void unEscapesDeeplyNestedText() {
        final String escaped = "\"" + deepJson(20).replace("\"", "\\\"") + "\"";
        final String result = unEscaper.process(escaped);
        assertAll(
                () -> assertTrue(result.startsWith("{"), "深层嵌套去转义结果应为 JSON 对象"),
                () -> assertTrue(result.contains("\n"), "深层嵌套去转义结果应为多行"),
                () -> assertTrue(result.contains("\"n\":1"), "深层嵌套去转义结果应保留叶子字段")
        );
    }

    @Test
    @DisplayName("异常：非法输入原样返回")
    void returnsInputOnGarbage() {
        assertAll(
                () -> assertEquals("not a json", unEscaper.process("not a json"), "无法解析的输入应原样返回"),
                () -> assertEquals("\"\"\"", unEscaper.process("\"\"\""), "残缺引号输入应原样返回")
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
