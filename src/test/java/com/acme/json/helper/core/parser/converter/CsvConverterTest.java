package com.acme.json.helper.core.parser.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV 转换器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class CsvConverterTest {

    private final CsvConverter converter = new CsvConverter();

    @Test
    @DisplayName("正常：对象数组转换为含表头的 CSV")
    void convertsObjectArrayToCsvWithHeader() {
        final String csv = converter.convert("[{\"name\":\"tom\",\"age\":18},{\"name\":\"ann\",\"age\":20}]");
        assertAll(
                () -> assertTrue(csv.contains("name,age"), "CSV 首行应为字段表头"),
                () -> assertTrue(csv.contains("tom,18"), "CSV 应包含第一行数据"),
                () -> assertTrue(csv.contains("ann,20"), "CSV 应包含第二行数据")
        );
    }

    @Test
    @DisplayName("边界：单个 JSON 对象被包装为单行 CSV")
    void wrapsSingleObjectAsOneRow() {
        final String csv = converter.convert("{\"a\":1}");
        assertAll(
                () -> assertTrue(csv.contains("a"), "CSV 应包含表头 a"),
                () -> assertTrue(csv.contains("1"), "CSV 应包含数据 1")
        );
    }

    @Test
    @DisplayName("边界：嵌套对象展平为下划线拼接的列名")
    void flattensNestedObjectWithUnderscorePrefix() {
        final String csv = converter.convert("[{\"user\":{\"name\":\"tom\"},\"id\":1}]");
        assertAll(
                () -> assertTrue(csv.contains("user_name,id"), "嵌套对象应展平为 user_name 列"),
                () -> assertTrue(csv.contains("tom,1"), "展平后的数据行应包含嵌套字段值")
        );
    }

    @Test
    @DisplayName("边界：元素结构不一致时表头取所有对象字段并集")
    void mergesHeadersAcrossRows() {
        final String csv = converter.convert("[{\"a\":1},{\"b\":2}]");
        assertTrue(csv.contains("a,b"), "表头应为所有对象字段的有序并集");
    }

    @Test
    @DisplayName("异常：非法 JSON 输入返回空串而非抛异常")
    void returnsEmptyStringOnInvalidJson() {
        assertEquals("", converter.convert("not a json"), "解析失败时 convert 吞掉异常并返回空串");
    }

    @Test
    @DisplayName("异常：非对象元素数组返回空串")
    void returnsEmptyStringOnNonObjectArray() {
        assertEquals("", converter.convert("[1,2,3]"),
                "数组元素强转 JSONObject 失败，convert 吞掉异常并返回空串");
    }

    @Test
    @DisplayName("默认：reverseConvert 未覆写，原样返回输入")
    void reverseConvertReturnsInputAsIs() {
        final String csv = "a,b\n1,2";
        assertEquals(csv, converter.reverseConvert(csv), "CsvConverter 未覆写 reverseConvert，走接口默认实现原样返回");
    }
}
