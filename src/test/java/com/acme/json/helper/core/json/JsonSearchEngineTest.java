package com.acme.json.helper.core.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON搜索引擎单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class JsonSearchEngineTest {

    /** 简单 JSON 对象样本 */
    private static final String SAMPLE_JSON = "{\"a\":1}";

    private final JsonSearchEngine searchEngine = new JsonSearchEngine();

    @Nested
    @DisplayName("正常：JsonPath 查询")
    class NormalQueries {

        @Test
        @DisplayName("正常：标量字段路径返回字面值")
        void queriesScalarFields() {
            assertAll(
                    () -> assertEquals("1", searchEngine.process("{\"a\":1}", "$.a"), "数字字段应返回字面值"),
                    () -> assertEquals("true", searchEngine.process("{\"a\":true}", "$.a"), "布尔字段应返回字面值"),
                    () -> assertEquals("x", searchEngine.process("{\"items\":[{\"name\":\"x\"}]}", "$.items[0].name"),
                            "数组索引路径应返回命中的字符串字段")
            );
        }

        @Test
        @DisplayName("正常：对象与数组路径返回格式化 JSON")
        void queriesObjectAndArray() {
            final String objectResult = searchEngine.process("{\"a\":{\"b\":2}}", "$.a");
            final String arrayResult = searchEngine.process("{\"list\":[1,2,3]}", "$.list");
            assertAll(
                    () -> assertTrue(objectResult.contains("\"b\":2"), "对象路径结果应保留字段"),
                    () -> assertTrue(objectResult.contains("\n"), "对象路径结果应被格式化为多行"),
                    () -> assertTrue(arrayResult.startsWith("["), "数组路径结果应为 JSON 数组"),
                    () -> assertTrue(arrayResult.contains("\n"), "数组路径结果应被格式化为多行")
            );
        }

        @Test
        @DisplayName("正常：根路径返回整体格式化 JSON")
        void queriesRoot() {
            final String result = searchEngine.process(SAMPLE_JSON, "$");
            assertAll(
                    () -> assertTrue(result.contains("\"a\":1"), "根路径结果应保留全部字段"),
                    () -> assertTrue(result.contains("\n"), "根路径结果应被格式化为多行")
            );
        }

        @Test
        @DisplayName("边界：深层嵌套路径返回格式化子树")
        void queriesDeepPath() {
            final String result = searchEngine.process(deepJson(20), "$.v.n.n.n.n.n");
            assertAll(
                    () -> assertFalse(result.isEmpty(), "深层路径结果不应为空"),
                    () -> assertTrue(result.contains("\"n\":1"), "深层路径结果应包含叶子字段"),
                    () -> assertTrue(result.contains("\n"), "深层路径结果应被格式化为多行")
            );
        }
    }

    @Nested
    @DisplayName("边界：空值降级")
    class BlankFallback {

        @Test
        @DisplayName("边界：路径不存在返回空串")
        void returnsEmptyOnMissingPath() {
            assertAll(
                    () -> assertEquals("", searchEngine.process(SAMPLE_JSON, "$.missing"), "缺失路径应返回空串"),
                    () -> assertEquals("", searchEngine.process("{}", "$.a"), "空对象查询应返回空串")
            );
        }

        @Test
        @DisplayName("边界：查询结果为 null 字面量字符串时被过滤为空串")
        void filtersNullLiteral() {
            assertEquals("", searchEngine.process("{\"a\":\"null\"}", "$.a"),
                    "字符串 \"null\" 字面值应被过滤为空串");
        }

        @ParameterizedTest(name = "[{index}] JSON 输入为空时返回空串")
        @NullAndEmptySource
        @DisplayName("边界：null 与空 JSON 输入返回空串")
        void returnsEmptyOnBlankJson(final String json) {
            assertEquals("", searchEngine.process(json, "$.a"), "空 JSON 输入应返回空串");
        }
    }

    @Nested
    @DisplayName("异常：解析失败降级")
    class ExceptionFallback {

        @ParameterizedTest(name = "[{index}] 表达式 [{1}] 作用于 [{0}] 返回空串")
        @MethodSource("com.acme.json.helper.core.json.JsonSearchEngineTest#failingQueries")
        @DisplayName("异常：非法 JSON 或非法表达式返回空串")
        void returnsEmptyOnFailure(final String json, final String expression) {
            assertEquals("", searchEngine.process(json, expression), "解析异常时应降级返回空串");
        }
    }

    static Stream<Arguments> failingQueries() {
        return Stream.of(
                Arguments.of("not a json", "$.a"),
                Arguments.of(SAMPLE_JSON, "$.["),
                Arguments.of(SAMPLE_JSON, ""),
                Arguments.of(SAMPLE_JSON, null)
        );
    }

    private static String deepJson(final int depth) {
        final StringBuilder sb = new StringBuilder("{\"v\":");
        for (int i = 0; i < depth; i++) {
            sb.append("{\"n\":");
        }
        sb.append("1");
        for (int i = 0; i < depth; i++) {
            sb.append("}");
        }
        return sb.append("}").toString();
    }
}
