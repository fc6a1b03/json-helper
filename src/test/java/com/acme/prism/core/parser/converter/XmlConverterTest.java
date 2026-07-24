package com.acme.prism.core.parser.converter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XML 转换器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class XmlConverterTest {

    private final XmlConverter converter = new XmlConverter();

    @Test
    @DisplayName("正常：JSON 对象转换为带 dummy 根节点的 XML")
    void convertsJsonObjectToXmlWithDummyRoot() {
        final String xml = converter.convert("{\"name\":\"tom\",\"age\":18}");
        assertAll(
                () -> assertTrue(xml.contains("<dummy>"), "XML 输出应包含 dummy 根节点"),
                () -> assertTrue(xml.contains("<name>tom</name>"), "XML 输出应包含字符串字段元素"),
                () -> assertTrue(xml.contains("<age>18</age>"), "XML 输出应包含数字字段元素")
        );
    }

    @Test
    @DisplayName("边界：嵌套 JSON 对象转换为嵌套 XML 元素")
    void convertsNestedJsonObject() {
        final String xml = converter.convert("{\"addr\":{\"city\":\"bj\"}}");
        assertAll(
                () -> assertTrue(xml.contains("<dummy>"), "XML 输出应包含 dummy 根节点"),
                () -> assertTrue(xml.contains("<addr>"), "XML 输出应包含嵌套对象元素"),
                () -> assertTrue(xml.contains("<city>bj</city>"), "XML 输出应包含嵌套字段元素")
        );
    }

    @Test
    @DisplayName("边界：空 JSON 对象转换为仅含 dummy 根的 XML")
    void convertsEmptyJsonObject() {
        final String xml = converter.convert("{}");
        assertTrue(xml.contains("dummy"), "空对象仍应输出 dummy 根节点");
        assertFalse(xml.contains("<name>"), "空对象不应包含任何字段元素");
    }

    @Test
    @DisplayName("异常：非法 JSON 转换时抛出 JSONException")
    void throwsOnInvalidJson() {
        assertThrows(JSONException.class, () -> converter.convert("not a json"),
                "JSON.parse 对非法输入抛出 fastjson2 JSONException");
    }

    @Test
    @DisplayName("正常：XML 反向转换为 JSON，叶子节点值全部为字符串")
    void reverseConvertsXmlToJson() {
        final String json = converter.reverseConvert("<dummy><name>tom</name><age>18</age></dummy>");
        final JSONObject obj = JSON.parseObject(json);
        assertAll(
                () -> assertEquals("tom", obj.getString("name"), "XML 文本节点应反序列化为字符串"),
                () -> assertEquals("18", obj.getString("age"), "XML 无类型系统，数字节点值应为字符串")
        );
    }

    @Test
    @DisplayName("边界：嵌套 XML 反向转换为嵌套 JSON 对象")
    void reverseConvertsNestedXml() {
        final String json = converter.reverseConvert("<dummy><addr><city>bj</city></addr></dummy>");
        assertEquals("bj", JSON.parseObject(json).getJSONObject("addr").getString("city"),
                "嵌套 XML 元素应反序列化为嵌套 JSON 对象");
    }

    @Test
    @DisplayName("异常：非法 XML 反向转换时抛出运行时异常")
    void reverseThrowsOnInvalidXml() {
        assertThrows(RuntimeException.class, () -> converter.reverseConvert("not xml at all"),
                "Jackson XmlMapper 对非法 XML 抛出运行时异常");
    }

    @Test
    @DisplayName("往返：JSON→XML→JSON 数据等价")
    void roundTripPreservesData() {
        final String xml = converter.convert("{\"name\":\"tom\"}");
        final String json = converter.reverseConvert(xml);
        assertEquals("tom", JSON.parseObject(json).getString("name"), "往返转换后字段值应保持等价");
    }
}
