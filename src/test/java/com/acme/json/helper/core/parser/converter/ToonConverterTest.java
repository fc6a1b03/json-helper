package com.acme.json.helper.core.parser.converter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOON 转换器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class ToonConverterTest {

    private final ToonConverter converter = new ToonConverter();

    @Test
    @DisplayName("正常：JSON 对象转换为 TOON 键值结构")
    void convertsJsonObjectToToon() {
        final String toon = converter.convert("{\"name\":\"tom\",\"age\":18}");
        assertAll(
                () -> assertTrue(toon.contains("name"), "TOON 输出应包含字段名"),
                () -> assertTrue(toon.contains("tom"), "TOON 输出应包含字符串字段值"),
                () -> assertTrue(toon.contains("age"), "TOON 输出应包含数字字段名"),
                () -> assertTrue(toon.contains("18"), "TOON 输出应包含数字字段值")
        );
    }

    @Test
    @DisplayName("边界：JSON 数组转换为 TOON 长度声明结构")
    void convertsJsonArrayToToon() {
        final String toon = converter.convert("[1,2,3]");
        assertAll(
                () -> assertTrue(toon.contains("1"), "TOON 输出应包含数组元素"),
                () -> assertTrue(toon.contains("3"), "TOON 输出应包含数组元素")
        );
    }

    @Test
    @DisplayName("异常：非法 JSON 转换时抛出运行时异常")
    void throwsOnInvalidJson() {
        assertThrows(RuntimeException.class, () -> converter.convert("not a json"),
                "JToon.encodeJson 对非法 JSON 抛出异常");
    }

    @Test
    @DisplayName("正常：TOON 反向转换为格式化 JSON")
    void reverseConvertsToonToJson() {
        final String toon = converter.convert("{\"name\":\"tom\",\"age\":18}");
        final JSONObject obj = JSON.parseObject(converter.reverseConvert(toon));
        assertAll(
                () -> assertEquals("tom", obj.getString("name"), "TOON 字符串应反序列化为 JSON 字符串"),
                () -> assertEquals(18, obj.getIntValue("age"), "TOON 数字应反序列化为 JSON 数字")
        );
    }

    @Test
    @DisplayName("边界：STRICT 解码失败时回退 RELAXED 解码")
    void reverseFallsBackToRelaxedDecoding() {
        // 第二行缩进为 1 空格，非 indent=2 的整数倍：STRICT 校验拒绝，RELAXED 放行
        final String result = converter.reverseConvert("a: 1\n b: 2");
        final JSONObject obj = JSON.parseObject(result);
        assertAll(
                () -> assertEquals(1, obj.getIntValue("a"), "RELAXED 解码应恢复字段 a"),
                () -> assertEquals(2, obj.getIntValue("b"), "RELAXED 解码应恢复字段 b")
        );
    }

    @Test
    @DisplayName("边界：无结构文本被解码为 primitive 字符串")
    void reverseDecodesUnstructuredTextAsStringPrimitive() {
        final String result = converter.reverseConvert("%%% not toon at all %%%");
        assertEquals("%%% not toon at all %%%", JSON.parse(result),
                "无 key 的单行文本应被解码为 JSON 字符串 primitive");
    }

    @Test
    @DisplayName("异常：STRICT 与 RELAXED 均解码失败时原样返回")
    void reverseReturnsInputWhenBothDecodingsFail() {
        // 数组长度声明（5）与实际元素数（2）不一致：两种模式均拒绝
        final String garbage = "a[5]:\t1\t2";
        assertEquals(garbage, converter.reverseConvert(garbage), "双重解码均失败时应原样返回输入");
    }

    @Test
    @DisplayName("往返：JSON→TOON→JSON 数据等价")
    void roundTripPreservesData() {
        final String toon = converter.convert("{\"name\":\"tom\",\"tags\":[\"a\",\"b\"]}");
        final JSONObject obj = JSON.parseObject(converter.reverseConvert(toon));
        assertAll(
                () -> assertEquals("tom", obj.getString("name"), "往返转换后字符串字段值应保持等价"),
                () -> assertEquals(JSON.parseArray("[\"a\",\"b\"]"), obj.getJSONArray("tags"), "往返转换后数组字段值应保持等价")
        );
    }
}
