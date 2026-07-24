package com.acme.prism.core.parser.converter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TOML 转换器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class TomlConverterTest {

    private final TomlConverter converter = new TomlConverter();

    @Test
    @DisplayName("正常：JSON 对象转换为 TOML 键值结构")
    void convertsJsonObjectToToml() {
        final String toml = converter.convert("{\"name\":\"tom\",\"age\":18}");
        assertAll(
                () -> assertTrue(toml.contains("name"), "TOML 输出应包含字段名"),
                () -> assertTrue(toml.contains("tom"), "TOML 输出应包含字符串字段值"),
                () -> assertTrue(toml.contains("age"), "TOML 输出应包含数字字段名"),
                () -> assertTrue(toml.contains("18"), "TOML 输出应包含数字字段值")
        );
    }

    @Test
    @DisplayName("边界：JSON 数组转换时被包装为 dummy 键")
    void wrapsJsonArrayWithDummyKey() {
        final String toml = converter.convert("[1,2,3]");
        assertTrue(toml.contains("dummy"), "数组 JSON 应包装为 dummy 根键");
    }

    @Test
    @DisplayName("边界：嵌套 JSON 对象转换为 TOML 表结构")
    void convertsNestedJsonObject() {
        final String toml = converter.convert("{\"server\":{\"host\":\"localhost\"}}");
        assertAll(
                () -> assertTrue(toml.contains("server"), "TOML 输出应包含嵌套表名"),
                () -> assertTrue(toml.contains("host"), "TOML 输出应包含嵌套字段名"),
                () -> assertTrue(toml.contains("localhost"), "TOML 输出应包含嵌套字段值")
        );
    }

    @Test
    @DisplayName("异常：非法 JSON 转换时抛出 JSONException")
    void throwsOnInvalidJson() {
        assertThrows(JSONException.class, () -> converter.convert("not a json"),
                "JSON.parse 对非法输入抛出 fastjson2 JSONException");
    }

    @Test
    @DisplayName("正常：TOML 反向转换为 JSON，数字保留数字类型")
    void reverseConvertsTomlToJson() {
        final String json = converter.reverseConvert("name = \"tom\"\nage = 18\n");
        final JSONObject obj = JSON.parseObject(json);
        assertAll(
                () -> assertEquals("tom", obj.getString("name"), "TOML 字符串应反序列化为 JSON 字符串"),
                () -> assertEquals(18, obj.getIntValue("age"), "TOML 整数应反序列化为 JSON 数字")
        );
    }

    @Test
    @DisplayName("边界：TOML 表反向转换为嵌套 JSON 对象")
    void reverseConvertsTomlTable() {
        final String json = converter.reverseConvert("[server]\nhost = \"localhost\"\n");
        assertEquals("localhost", JSON.parseObject(json).getJSONObject("server").getString("host"),
                "TOML 表应反序列化为嵌套 JSON 对象");
    }

    @Test
    @DisplayName("异常：非法 TOML 反向转换时抛出运行时异常")
    void reverseThrowsOnInvalidToml() {
        assertThrows(RuntimeException.class, () -> converter.reverseConvert("key = [unclosed"),
                "Jackson TomlMapper 对非法 TOML 抛出运行时异常");
    }

    @Test
    @DisplayName("往返：JSON→TOML→JSON 数据等价")
    void roundTripPreservesData() {
        final String toml = converter.convert("{\"name\":\"tom\",\"age\":18}");
        final JSONObject obj = JSON.parseObject(converter.reverseConvert(toml));
        assertAll(
                () -> assertEquals("tom", obj.getString("name"), "往返转换后字符串字段值应保持等价"),
                () -> assertEquals(18, obj.getIntValue("age"), "往返转换后数字字段值应保持等价")
        );
    }
}
