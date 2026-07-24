package com.acme.prism.core.parser.converter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YAML 转换器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class YamlConverterTest {

    private final YamlConverter converter = new YamlConverter();

    @Test
    @DisplayName("正常：JSON 对象转换为 YAML 键值结构")
    void convertsJsonObjectToYaml() {
        final String yaml = converter.convert("{\"name\":\"tom\",\"age\":18}");
        assertAll(
                // Jackson 3 YAML 默认以双引号风格输出字符串标量
                () -> assertTrue(yaml.contains("name: \"tom\""), "YAML 输出应包含带引号的字符串字段"),
                () -> assertTrue(yaml.contains("age: 18"), "YAML 输出应包含数字字段")
        );
    }

    @Test
    @DisplayName("边界：JSON 数组转换为 YAML 列表项")
    void convertsJsonArrayToYamlList() {
        final String yaml = converter.convert("[1,2]");
        assertAll(
                () -> assertTrue(yaml.contains("- 1"), "YAML 输出应包含第一个列表项"),
                () -> assertTrue(yaml.contains("- 2"), "YAML 输出应包含第二个列表项")
        );
    }

    @Test
    @DisplayName("异常：非法 JSON 转换时抛出 JSONException")
    void throwsOnInvalidJson() {
        assertThrows(JSONException.class, () -> converter.convert("not a json"),
                "JSON.parse 对非法输入抛出 fastjson2 JSONException");
    }

    @Test
    @DisplayName("正常：单文档 YAML 反向转换为 JSON 对象")
    void reverseConvertsSingleDocument() {
        final String json = converter.reverseConvert("name: tom\nage: 18\n");
        final JSONObject obj = JSON.parseObject(json);
        assertAll(
                () -> assertEquals("tom", obj.getString("name"), "YAML 字符串应反序列化为 JSON 字符串"),
                () -> assertEquals(18, obj.getIntValue("age"), "YAML 数字应反序列化为 JSON 数字")
        );
    }

    @Test
    @DisplayName("正常：多文档 YAML 反向转换为 JSON 数组")
    void reverseConvertsMultipleDocumentsToArray() {
        final String json = converter.reverseConvert("---\na: 1\n---\nb: 2\n");
        final JSONArray array = assertInstanceOf(JSONArray.class, JSON.parse(json),
                "多文档 YAML 应反向转换为 JSON 数组");
        assertAll(
                () -> assertEquals(2, array.size(), "JSON 数组应包含两个文档"),
                () -> assertEquals(1, array.getJSONObject(0).getIntValue("a"), "第一个文档的字段 a 应为 1"),
                () -> assertEquals(2, array.getJSONObject(1).getIntValue("b"), "第二个文档的字段 b 应为 2")
        );
    }

    @Test
    @DisplayName("边界：空字符串反向转换原样返回")
    void reverseReturnsInputOnEmptyString() {
        assertEquals("", converter.reverseConvert(""), "无任何 YAML 文档时应原样返回输入");
    }

    @Test
    @DisplayName("异常：非法 YAML 反向转换原样返回")
    void reverseReturnsInputOnInvalidYaml() {
        final String garbage = "a: [unclosed";
        assertEquals(garbage, converter.reverseConvert(garbage), "解析失败时应原样返回输入");
    }

    @Test
    @DisplayName("往返：JSON→YAML→JSON 数据等价")
    void roundTripPreservesData() {
        final String yaml = converter.convert("{\"name\":\"tom\",\"age\":18}");
        final JSONObject obj = JSON.parseObject(converter.reverseConvert(yaml));
        assertAll(
                () -> assertEquals("tom", obj.getString("name"), "往返转换后字符串字段值应保持等价"),
                () -> assertEquals(18, obj.getIntValue("age"), "往返转换后数字字段值应保持等价")
        );
    }
}
