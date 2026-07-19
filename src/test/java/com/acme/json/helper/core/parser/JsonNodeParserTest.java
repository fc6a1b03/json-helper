package com.acme.json.helper.core.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 节点解析器（JsonNodeParser）单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class JsonNodeParserTest {

    @Test
    @DisplayName("正常：对象 JSON 构建 Object 节点及子节点")
    void parsesObjectNode() {
        final JsonNodeParser.JsonNode root = JsonNodeParser.parse("root", "{\"a\":1}");
        assertAll(
                () -> assertEquals("root", root.key(), "根节点 key 应为入参 key"),
                () -> assertEquals("Object", root.type(), "JSONObject 值类型应为 Object"),
                () -> assertEquals("{\"a\":1}", root.value(), "value() 应返回值的 JSON 字符串"),
                () -> assertEquals(1, root.children().size(), "对象子节点数应与字段数一致"),
                () -> assertEquals("a", root.children().getFirst().key(), "子节点 key 应为字段名"),
                () -> assertEquals("Integer", root.children().getFirst().type(), "数字字段类型应为 Integer"),
                () -> assertEquals("1", root.children().getFirst().value(), "数字字段 value() 应为其 JSON 字符串"),
                () -> assertTrue(root.children().getFirst().children().isEmpty(), "叶子节点不应有子节点")
        );
    }

    @Test
    @DisplayName("正常：数组 JSON 构建 Array 节点并以 [索引] 为子节点 key")
    void parsesArrayNode() {
        final JsonNodeParser.JsonNode root = JsonNodeParser.parse("root", "[1,\"x\"]");
        assertAll(
                () -> assertEquals("Array", root.type(), "JSONArray 值类型应为 Array"),
                () -> assertEquals(2, root.children().size(), "数组子节点数应与元素数一致"),
                () -> assertEquals("[0]", root.children().get(0).key(), "数组子节点 key 应为 [索引] 形式"),
                () -> assertEquals("[1]", root.children().get(1).key(), "数组子节点 key 应为 [索引] 形式"),
                () -> assertEquals("Integer", root.children().get(0).type(), "数字元素类型应为 Integer"),
                () -> assertEquals("String", root.children().get(1).type(), "字符串元素类型应为 String"),
                () -> assertEquals("\"x\"", root.children().get(1).value(), "字符串值 value() 应为带引号的 JSON 字符串")
        );
    }

    @Test
    @DisplayName("正常：嵌套 JSON 逐层构建节点树")
    void parsesNestedNode() {
        final JsonNodeParser.JsonNode root = JsonNodeParser.parse("root", "{\"a\":{\"b\":[true]}}");
        final JsonNodeParser.JsonNode nodeA = root.children().getFirst();
        final JsonNodeParser.JsonNode nodeB = nodeA.children().getFirst();
        final JsonNodeParser.JsonNode element = nodeB.children().getFirst();
        assertAll(
                () -> assertEquals("Object", root.type(), "根节点类型应为 Object"),
                () -> assertEquals("a", nodeA.key(), "子节点 key 应为字段名"),
                () -> assertEquals("Object", nodeA.type(), "嵌套对象类型应为 Object"),
                () -> assertEquals("b", nodeB.key(), "嵌套子节点 key 应为字段名"),
                () -> assertEquals("Array", nodeB.type(), "嵌套数组类型应为 Array"),
                () -> assertEquals("[0]", element.key(), "数组元素 key 应为 [索引] 形式"),
                () -> assertEquals("Boolean", element.type(), "布尔元素类型应为 Boolean"),
                () -> assertEquals("true", element.value(), "布尔元素 value() 应为其 JSON 字符串")
        );
    }

    @Test
    @DisplayName("边界：标量数字 JSON 构建叶子节点")
    void parsesScalarNumber() {
        final JsonNodeParser.JsonNode node = JsonNodeParser.parse("num", "42");
        assertAll(
                () -> assertEquals("Integer", node.type(), "标量数字类型应为 Integer"),
                () -> assertEquals("42", node.value(), "value() 应返回值的 JSON 字符串"),
                () -> assertTrue(node.children().isEmpty(), "标量节点不应有子节点")
        );
    }

    @Test
    @DisplayName("边界：JSON null 构建空值节点")
    void parsesJsonNull() {
        final JsonNodeParser.JsonNode node = JsonNodeParser.parse("root", "null");
        assertAll(
                () -> assertEquals("", node.type(), "null 值类型应为空串"),
                () -> assertEquals("", node.value(), "null 值 value() 应为空串"),
                () -> assertEquals("", node.toString(), "null 值 toString() 应为空串"),
                () -> assertTrue(node.children().isEmpty(), "null 值节点不应有子节点")
        );
    }

    @Test
    @DisplayName("边界：对象内 null 字段构建空值子节点")
    void parsesNullField() {
        final JsonNodeParser.JsonNode child = JsonNodeParser.parse("root", "{\"a\":null}").children().getFirst();
        assertAll(
                () -> assertEquals("a", child.key(), "子节点 key 应为字段名"),
                () -> assertEquals("", child.type(), "null 字段类型应为空串"),
                () -> assertEquals("", child.value(), "null 字段 value() 应为空串")
        );
    }

    @Test
    @DisplayName("异常：非法 JSON 按原始文本构建 String 叶子节点")
    void parsesPlainTextAsLeaf() {
        final JsonNodeParser.JsonNode node = JsonNodeParser.parse("root", "not a json");
        assertAll(
                () -> assertEquals("String", node.type(), "原始文本应按 String 值处理"),
                () -> assertEquals("\"not a json\"", node.value(), "value() 应返回原文本的 JSON 字符串（带引号）"),
                () -> assertTrue(node.children().isEmpty(), "文本叶子节点不应有子节点"),
                () -> assertEquals("{\"root\": \"not a json\"}", node.toString(), "toString() 应输出 key 与 JSON 字符串值")
        );
    }
}
