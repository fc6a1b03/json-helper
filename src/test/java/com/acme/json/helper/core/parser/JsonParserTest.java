package com.acme.json.helper.core.parser;

import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 与任何文件互转（JsonParser 注册表分发）单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class JsonParserTest {

    private static final String SAMPLE_JSON = "{\"name\":\"acme\",\"age\":18}";

    @Test
    @DisplayName("正常：convert 分发到 XML 转换器")
    void convertsJsonToXml() {
        final String result = JsonParser.convert(SAMPLE_JSON, AnyFile.XML);
        assertAll(
                () -> assertTrue(result.contains("<dummy>"), "XML 输出应使用 dummy 根节点"),
                () -> assertTrue(result.contains("name"), "XML 输出应包含字段名")
        );
    }

    @Test
    @DisplayName("正常：convert 分发到 YAML 转换器")
    void convertsJsonToYaml() {
        assertTrue(JsonParser.convert(SAMPLE_JSON, AnyFile.YAML).contains("name"), "YAML 输出应包含字段名");
    }

    @Test
    @DisplayName("正常：convert 分发到 TOML 转换器")
    void convertsJsonToToml() {
        assertTrue(JsonParser.convert(SAMPLE_JSON, AnyFile.TOML).contains("name"), "TOML 输出应包含字段名");
    }

    @Test
    @DisplayName("正常：convert 分发到 PROPERTIES 转换器")
    void convertsJsonToProperties() {
        assertTrue(JsonParser.convert(SAMPLE_JSON, AnyFile.PROPERTIES).contains("name"), "PROPERTIES 输出应包含字段名");
    }

    @Test
    @DisplayName("正常：convert 分发到 TOON 转换器")
    void convertsJsonToToon() {
        assertTrue(JsonParser.convert(SAMPLE_JSON, AnyFile.TOON).contains("name"), "TOON 输出应包含字段名");
    }

    @Test
    @DisplayName("正常：convert 分发到 URL_PARAMS 转换器并按键值对输出")
    void convertsJsonToUrlParams() {
        assertEquals("name=acme&age=18", JsonParser.convert(SAMPLE_JSON, AnyFile.URL_PARAMS),
                "URL 参数输出应为 key=value 形式并按 & 连接");
    }

    @Test
    @DisplayName("正常：convert 分发到 BASE64 转换器并输出标准 Base64")
    void convertsJsonToBase64() {
        final String expected = Base64.getEncoder().encodeToString(SAMPLE_JSON.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, JsonParser.convert(SAMPLE_JSON, AnyFile.BASE64), "BASE64 输出应为 UTF-8 标准编码");
    }

    @Test
    @DisplayName("正常：reverseConvert 分发到 XML 转换器")
    void reverseConvertsXml() {
        final String result = JsonParser.reverseConvert("<root><name>acme</name></root>", AnyFile.XML);
        assertAll(
                () -> assertTrue(JSON.isValid(result), "反向转换结果应为合法 JSON"),
                () -> assertTrue(result.contains("acme"), "反向转换结果应包含 XML 文本内容")
        );
    }

    @Test
    @DisplayName("正常：reverseConvert 分发到 YAML 转换器")
    void reverseConvertsYaml() {
        final String result = JsonParser.reverseConvert("name: acme", AnyFile.YAML);
        assertAll(
                () -> assertTrue(JSON.isValid(result), "反向转换结果应为合法 JSON"),
                () -> assertTrue(result.contains("name"), "反向转换结果应包含 YAML 键名")
        );
    }

    @Test
    @DisplayName("正常：reverseConvert 分发到 URL_PARAMS 转换器")
    void reverseConvertsUrlParams() {
        final String result = JsonParser.reverseConvert("a=1&b=true", AnyFile.URL_PARAMS);
        assertAll(
                () -> assertTrue(JSON.isValid(result), "反向转换结果应为合法 JSON"),
                () -> assertTrue(result.contains("\"a\""), "反向转换结果应包含参数名")
        );
    }

    @Test
    @DisplayName("正常：reverseConvert 分发到 BASE64 转换器并解码")
    void reverseConvertsBase64() {
        final String encoded = Base64.getEncoder().encodeToString("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        final String result = JsonParser.reverseConvert(encoded, AnyFile.BASE64);
        assertAll(
                () -> assertTrue(JSON.isValid(result), "反向转换结果应为合法 JSON"),
                () -> assertTrue(result.contains("\"a\""), "反向转换结果应包含解码后的键名")
        );
    }

    @Test
    @DisplayName("边界：reverseConvert 传 null 格式返回空串")
    void reverseConvertWithNullFormatReturnsEmpty() {
        assertEquals("", JsonParser.reverseConvert("a=1", null), "null 格式应直接返回空串");
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.INCLUDE, names = {"JSON", "TEXT"})
    @DisplayName("异常：convert 对未注册格式抛 IllegalArgumentException")
    void convertThrowsForUnregisteredFormat(final AnyFile format) {
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonParser.convert(SAMPLE_JSON, format), "未注册格式应抛 IllegalArgumentException");
        assertEquals("不支持的格式", exception.getMessage(), "异常消息应说明不支持的格式");
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.INCLUDE, names = {"JSON", "TEXT"})
    @DisplayName("异常：reverseConvert 对未注册格式抛 IllegalArgumentException")
    void reverseConvertThrowsForUnregisteredFormat(final AnyFile format) {
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonParser.reverseConvert("anything", format), "未注册格式应抛 IllegalArgumentException");
        assertEquals("不支持的格式", exception.getMessage(), "异常消息应说明不支持的格式");
    }
}
