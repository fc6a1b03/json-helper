package com.acme.json.helper.core.parser.converter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BASE64 转换器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class Base64ConverterTest {

    private final Base64Converter converter = new Base64Converter();

    @Test
    @DisplayName("正常：JSON 文本编码为标准 BASE64")
    void encodesJsonToBase64() {
        assertEquals("eyJhIjoxfQ==", converter.convert("{\"a\":1}"),
                "UTF-8 标准 BASE64 编码结果是确定的");
    }

    @Test
    @DisplayName("边界：空字符串编码结果为空串")
    void encodesEmptyStringToEmpty() {
        assertEquals("", converter.convert(""), "空输入的 BASE64 编码应为空串");
    }

    @Test
    @DisplayName("正常：BASE64 解码后 JSON 被格式化输出")
    void decodesBase64AndFormats() {
        final String json = converter.reverseConvert("eyJhIjoxfQ==");
        final JSONObject obj = JSON.parseObject(json);
        assertAll(
                () -> assertEquals(1, obj.getIntValue("a"), "解码后的 JSON 应包含字段 a 的值 1"),
                () -> assertTrue(json.contains("\n"), "解码后的 JSON 应被 PrettyFormat 格式化为多行")
        );
    }

    @ParameterizedTest(name = "往返：{0}")
    @ValueSource(strings = {"{\"a\":1}", "{\"name\":\"tom\",\"tags\":[\"x\",\"y\"]}", "{\"nested\":{\"k\":\"v\"}}"})
    @DisplayName("往返：JSON→BASE64→JSON 数据等价")
    void roundTripPreservesData(final String input) {
        final String restored = converter.reverseConvert(converter.convert(input));
        assertEquals(JSON.parse(input), JSON.parse(restored), "往返转换后 JSON 语义应保持等价");
    }

    @Test
    @DisplayName("异常：BASE64 解码结果非 JSON 时原样返回解码文本")
    void returnsDecodedTextWhenNotJson() {
        // "aGVsbG8=" 解码后为 "hello"，非法 JSON 经 JsonFormatter 原样返回
        assertEquals("hello", converter.reverseConvert("aGVsbG8="),
                "解码结果非 JSON 时应原样返回解码文本");
    }

    @Test
    @DisplayName("边界：空 BASE64 串解码后输出 \"null\" 文本")
    void decodesEmptyBase64ToNullLiteral() {
        // 真实链路：hutool decodeStr("") 返回 null → JSON.parse(null) 返回 null → toJSONString(null) 得 "null"
        assertEquals("null", converter.reverseConvert(""), "空 BASE64 串最终输出 JSON null 字面量文本");
    }
}
