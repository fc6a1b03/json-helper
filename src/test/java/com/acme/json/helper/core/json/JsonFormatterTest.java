package com.acme.json.helper.core.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON 格式化器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class JsonFormatterTest {

    private final JsonFormatter formatter = new JsonFormatter();

    @Test
    @DisplayName("正常：压缩 JSON 被格式化为多行缩进")
    void formatsCompactJson() {
        final String result = formatter.process("{\"a\":1}");
        assertTrue(result.contains("\n"), "格式化结果应包含换行");
        assertTrue(result.contains("\"a\": 1") || result.contains("\"a\":1"), "格式化结果应保留字段");
    }

    @Test
    @DisplayName("边界：空对象格式化")
    void formatsEmptyObject() {
        assertEquals("{}", formatter.process("{}"), "空对象格式化后应原样输出");
    }

    @Test
    @DisplayName("异常：非法输入原样返回")
    void returnsInputOnInvalidJson() {
        final String garbage = "not a json";
        assertEquals(garbage, formatter.process(garbage), "非法输入应原样返回");
    }

    @Test
    @DisplayName("校验：isValid 对合法与非法输入的判断")
    void validatesJson() {
        assertTrue(formatter.isValid("{\"a\":1}"), "合法 JSON 应判定有效");
        assertFalse(formatter.isValid("not a json"), "非法 JSON 应判定无效");
    }
}
