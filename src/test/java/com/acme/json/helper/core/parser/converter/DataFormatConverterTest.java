package com.acme.json.helper.core.parser.converter;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据格式转换器接口静态与默认方法单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class DataFormatConverterTest {

    @Test
    @DisplayName("正常：jsonToObject 对 JSON 对象原样返回")
    void jsonToObjectPassesObjectThrough() {
        final JSONObject obj = DataFormatConverter.jsonToObject("{\"a\":1}");
        assertEquals(1, obj.getIntValue("a"), "JSON 对象应原样返回并保留字段值");
    }

    @Test
    @DisplayName("边界：jsonToObject 对数组取首个对象元素")
    void jsonToObjectTakesFirstArrayElement() {
        final JSONObject obj = DataFormatConverter.jsonToObject("[{\"a\":1},{\"b\":2}]");
        assertAll(
                () -> assertEquals(1, obj.getIntValue("a"), "应取数组首个元素"),
                () -> assertTrue(obj.isEmpty() || !obj.containsKey("b"), "数组后续元素应被忽略")
        );
    }

    @Test
    @DisplayName("边界：jsonToObject 对空数组返回空对象")
    void jsonToObjectReturnsEmptyObjectForEmptyArray() {
        assertTrue(DataFormatConverter.jsonToObject("[]").isEmpty(), "空数组应返回空 JSONObject");
    }

    @Test
    @DisplayName("边界：jsonToObject 对标量 JSON 返回空对象")
    void jsonToObjectReturnsEmptyObjectForScalar() {
        assertTrue(DataFormatConverter.jsonToObject("123").isEmpty(), "标量 JSON 应返回空 JSONObject");
    }

    @Test
    @DisplayName("默认：reverseConvert 接口默认实现原样返回输入")
    void defaultReverseConvertReturnsInputAsIs() {
        final DataFormatConverter passthrough = json -> json;
        assertEquals("anything", passthrough.reverseConvert("anything"), "接口默认 reverseConvert 应原样返回");
    }
}
