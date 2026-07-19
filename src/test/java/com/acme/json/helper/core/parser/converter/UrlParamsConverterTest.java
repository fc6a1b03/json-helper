package com.acme.json.helper.core.parser.converter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URL 参数转换器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class UrlParamsConverterTest {

    private final UrlParamsConverter converter = new UrlParamsConverter();

    @Test
    @DisplayName("正常：JSON 对象转换为 URL 参数串")
    void convertsMapToUrlParams() {
        assertEquals("a=1&b=x", converter.convert("{\"a\":1,\"b\":\"x\"}"),
                "JSON 对象应按声明顺序拼接为 key=value 参数对");
    }

    @Test
    @DisplayName("正常：JSON 数组转换为 itemN 下标参数串")
    void convertsListToIndexedUrlParams() {
        assertEquals("item0=a&item1=b", converter.convert("[\"a\",\"b\"]"),
                "JSON 数组应转换为 item0/item1 下标参数");
    }

    @Test
    @DisplayName("正常：特殊字符与非 ASCII 字符按 percent 编码")
    void encodesSpecialCharsWithPercentEncoding() {
        final String params = converter.convert("{\"q\":\"hello world\",\"n\":\"中\"}");
        assertAll(
                () -> assertTrue(params.contains("q=hello%20world"), "空格应编码为 %20"),
                () -> assertTrue(params.contains("n=%E4%B8%AD"), "中文应按 UTF-8 字节大写 hex 编码")
        );
    }

    @ParameterizedTest(name = "边界：{0} 转换为空串")
    @ValueSource(strings = {"{}", "[]"})
    @DisplayName("边界：空对象与空数组转换为空串")
    void convertsEmptyContainersToEmptyString(final String json) {
        assertEquals("", converter.convert(json), "空 Map/空 List 应转换为空串");
    }

    @Test
    @DisplayName("边界：标量 JSON 不是 Map/List 时原样返回")
    void returnsScalarJsonAsIs() {
        assertEquals("123", converter.convert("123"), "标量 JSON 应原样返回");
    }

    @Test
    @DisplayName("异常：非法 JSON 转换时原样返回")
    void returnsInputOnInvalidJson() {
        assertEquals("not a json", converter.convert("not a json"), "解析失败时 convert 吞掉异常并原样返回");
    }

    @Test
    @DisplayName("正常：URL 参数反向转换为 JSON 并推断值类型")
    void reverseConvertsWithTypeInference() {
        final JSONObject obj = JSON.parseObject(converter.reverseConvert("i=123&b=true&f=1.5&s=hello"));
        assertAll(
                () -> assertEquals(123, obj.getIntValue("i"), "整数文本应推断为数字"),
                () -> assertEquals(Boolean.TRUE, obj.getBoolean("b"), "true 文本应推断为布尔"),
                () -> assertEquals(1.5, obj.getDoubleValue("f"), "浮点文本应推断为浮点数"),
                () -> assertEquals("hello", obj.getString("s"), "普通文本应保持字符串")
        );
    }

    @Test
    @DisplayName("正常：percent 编码的参数反向解码")
    void reverseDecodesPercentEncodedValues() {
        final JSONObject obj = JSON.parseObject(converter.reverseConvert("q=hello%20world"));
        assertEquals("hello world", obj.getString("q"), "percent 编码值应被正确解码");
    }

    @Test
    @DisplayName("边界：无等号参数与空值参数均映射为空字符串")
    void reverseMapsMissingValueToEmptyString() {
        final JSONObject obj = JSON.parseObject(converter.reverseConvert("flag&empty="));
        assertAll(
                () -> assertEquals("", obj.getString("flag"), "无等号参数值应为空串"),
                () -> assertEquals("", obj.getString("empty"), "空值参数值应为空串")
        );
    }

    @Test
    @DisplayName("往返：JSON→URL 参数→JSON 数据等价")
    void roundTripPreservesData() {
        final String params = converter.convert("{\"a\":1,\"b\":\"x y\"}");
        final JSONObject obj = JSON.parseObject(converter.reverseConvert(params));
        assertAll(
                () -> assertEquals(1, obj.getIntValue("a"), "往返转换后整数字段值应保持等价"),
                () -> assertEquals("x y", obj.getString("b"), "往返转换后字符串字段值应保持等价")
        );
    }
}
