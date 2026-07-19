package com.acme.json.helper.core.parser;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通用格式自动识别（AnyParser）单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class AnyParserTest {

    @Test
    @DisplayName("正常：XML 样本被识别并转换为非空 JSON")
    void convertsXmlSample() {
        final String result = AnyParser.convert("<root><name>acme</name></root>");
        assertAll(
                () -> assertFalse(result.isEmpty(), "XML 样本应转换出非空 JSON"),
                () -> assertTrue(JSON.isValid(result), "转换结果应为合法 JSON"),
                () -> assertTrue(result.contains("acme"), "转换结果应包含 XML 文本内容")
        );
    }

    @Test
    @DisplayName("正常：YAML 样本被识别并转换为非空 JSON")
    void convertsYamlSample() {
        final String result = AnyParser.convert("name: 测试\nage: 18");
        assertAll(
                () -> assertFalse(result.isEmpty(), "YAML 样本应转换出非空 JSON"),
                () -> assertTrue(JSON.isValid(result), "转换结果应为合法 JSON"),
                () -> assertTrue(result.contains("name"), "转换结果应包含 YAML 键名")
        );
    }

    @Test
    @DisplayName("正常：TOML 样本被识别并转换为非空 JSON")
    void convertsTomlSample() {
        final String result = AnyParser.convert("title = \"demo\"\n[owner]\nname = \"tom\"");
        assertAll(
                () -> assertFalse(result.isEmpty(), "TOML 样本应转换出非空 JSON"),
                () -> assertTrue(JSON.isValid(result), "转换结果应为合法 JSON"),
                () -> assertTrue(result.contains("owner"), "转换结果应包含 TOML 表名")
        );
    }

    @Test
    @DisplayName("正常：PROPERTIES 样本被识别并转换为非空 JSON")
    void convertsPropertiesSample() {
        // 首字符为 '=' 使 URL_PARAMS 识别失败（键为空），才能落入 PROPERTIES 规则
        final String result = AnyParser.convert("=x\na=1");
        assertAll(
                () -> assertFalse(result.isEmpty(), "PROPERTIES 样本应转换出非空 JSON"),
                () -> assertTrue(JSON.isValid(result), "转换结果应为合法 JSON"),
                () -> assertTrue(result.contains("\"a\""), "转换结果应包含 PROPERTIES 键名")
        );
    }

    @Test
    @DisplayName("正常：BASE64 样本被识别并解码为非空 JSON")
    void convertsBase64Sample() {
        // "{\"a\":1}" 的 Base64 编码
        final String result = AnyParser.convert("eyJhIjoxfQ==");
        assertAll(
                () -> assertFalse(result.isEmpty(), "BASE64 样本应转换出非空 JSON"),
                () -> assertTrue(JSON.isValid(result), "转换结果应为合法 JSON"),
                () -> assertTrue(result.contains("\"a\""), "转换结果应包含解码后的 JSON 键名")
        );
    }

    @Test
    @DisplayName("正常：URL 参数样本被识别并转换为非空 JSON")
    void convertsUrlParamsSample() {
        final String result = AnyParser.convert("a=1&b=hello");
        assertAll(
                () -> assertFalse(result.isEmpty(), "URL 参数样本应转换出非空 JSON"),
                () -> assertTrue(JSON.isValid(result), "转换结果应为合法 JSON"),
                () -> assertTrue(result.contains("\"a\""), "转换结果应包含参数名")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"a\":1}", "[1,2,3]", "123", "true"})
    @DisplayName("边界：合法 JSON 输入返回空串")
    void returnsEmptyForValidJson(final String input) {
        assertTrue(AnyParser.convert(input).isEmpty(), "合法 JSON 无需转换，应返回空串");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t "})
    @DisplayName("边界：null 与空白输入返回空串")
    void returnsEmptyForBlankInput(final String input) {
        assertTrue(AnyParser.convert(input).isEmpty(), "空白输入应返回空串");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[", "[\"]", "{\"}", "{}", "[]", "{ }"})
    @DisplayName("边界：无识别价值的空白 JSON 样本返回空串")
    void returnsEmptyForSkippableSamples(final String input) {
        assertTrue(AnyParser.convert(input).isEmpty(), "可跳过样本应返回空串");
    }

    @ParameterizedTest
    @ValueSource(strings = {"这是一段垃圾文本，不包含任何结构化内容", "hello world", "!!!!!!"})
    @DisplayName("异常：垃圾文本识别失败返回空串")
    void returnsEmptyForGarbageText(final String input) {
        assertTrue(AnyParser.convert(input).isEmpty(), "无法识别的垃圾文本应返回空串");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a=1&b=2", "a=1", "flag&b=2"})
    @DisplayName("正常：isUrlParams 识别合法 URL 参数")
    void detectsUrlParams(final String input) {
        assertTrue(AnyParser.isUrlParams(input), "包含键值对或键的输入应识别为 URL 参数");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1", "abc", "&&"})
    @DisplayName("边界：isUrlParams 拒绝无键输入")
    void rejectsNonUrlParams(final String input) {
        assertFalse(AnyParser.isUrlParams(input), "缺少键或分隔符的输入不应识别为 URL 参数");
    }
}
