package com.acme.json.helper.core.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON修复器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class JsonRepairerTest {

    /** 简单 JSON 对象样本 */
    private static final String SAMPLE_JSON = "{\"a\":1}";

    private final JsonRepairer repairer = new JsonRepairer();

    @Test
    @DisplayName("正常：字段名缺失引号的 JSON 被修复")
    void repairsMissingQuotes() {
        assertEquals(SAMPLE_JSON, repairer.process("{a:1}"), "字段名缺失引号应被补全");
    }

    @Test
    @DisplayName("正常：未闭合的 JSON 数组被修复")
    void repairsUnclosedArray() {
        assertEquals("[1,2]", repairer.process("[1,2,"), "未闭合数组应被补全闭合");
    }

    @Test
    @DisplayName("正常：Markdown 代码块中的 JSON 被提取修复")
    void repairsMarkdownCodeBlock() {
        assertEquals("{\"a\":1}", repairer.process("```json\n{\"a\":1}\n```"),
                "标准修复失败后应降级为提取模式并取出代码块中的 JSON");
    }

    @Test
    @DisplayName("正常：合法 JSON 原样返回")
    void keepsValidJson() {
        assertEquals(SAMPLE_JSON, repairer.process(SAMPLE_JSON), "合法 JSON 应原样返回");
    }

    @Test
    @DisplayName("边界：深层嵌套合法 JSON 原样返回")
    void keepsDeeplyNestedJson() {
        final String deep = deepJson(20);
        assertEquals(deep, repairer.process(deep), "深层嵌套合法 JSON 应原样返回");
    }

    @ParameterizedTest(name = "[{index}] 空输入返回空串")
    @NullAndEmptySource
    @DisplayName("边界：null 与空字符串返回空串")
    void returnsEmptyOnBlankInput(final String input) {
        assertEquals("", repairer.process(input), "空输入应直接返回空串");
    }

    @ParameterizedTest(name = "[{index}] 无法修复的输入原样返回：{0}")
    @ValueSource(strings = {"{'a':1}", "{\"a\":1,}", "not a json at all", "answer is: {\"a\":1} done"})
    @DisplayName("异常：超出修复能力的输入按降级策略原样返回")
    void returnsInputWhenUnrepairable(final String input) {
        assertEquals(input, repairer.process(input), "两级修复均失败时应原样返回输入");
    }

    @Test
    @DisplayName("正常：Object 重载将输入转字符串后修复")
    void processesObjectInput() {
        assertAll(
                () -> assertEquals("", repairer.process((Object) null), "null 对象应返回空串"),
                () -> assertEquals("42", repairer.process((Object) Integer.valueOf(42)), "数字对象应转为字符串"),
                () -> assertEquals(SAMPLE_JSON, repairer.process((Object) "{a:1}"), "字符串对象应走修复流程")
        );
    }

    @Test
    @DisplayName("校验：isValid 对任意输入恒返回 true")
    void isValidAlwaysTrue() {
        assertAll(
                () -> assertTrue(repairer.isValid("{\"a\":1}"), "合法 JSON 应返回 true"),
                () -> assertTrue(repairer.isValid("not a json at all"), "非法 JSON 也返回 true"),
                () -> assertTrue(repairer.isValid(""), "空串也返回 true"),
                () -> assertTrue(repairer.isValid((String) null), "null 也返回 true")
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
