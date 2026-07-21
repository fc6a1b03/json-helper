package com.acme.json.helper.core.parser.converter;

import com.alibaba.fastjson2.JSONException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Record 转换器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class RecordConverterTest {

    private final RecordConverter converter = new RecordConverter();

    @Test
    @DisplayName("正常：JSON 对象生成紧凑构造的 record 源码")
    void generatesRecordWithCompactConstructor() {
        final String code = converter.convert("{\"name\":\"tom\",\"age\":18}");
        assertAll(
                () -> assertTrue(code.contains("public record Dummy("), "应生成 record 声明"),
                () -> assertTrue(code.contains("String name"), "record 组件应包含字符串字段"),
                () -> assertTrue(code.contains("Integer age"), "record 组件应包含整数字段"),
                () -> assertFalse(code.contains("lombok"), "record 不需要 lombok 导入"),
                () -> assertFalse(code.contains("@Data"), "record 不需要 @Data 注解")
        );
    }

    @Test
    @DisplayName("正常：嵌套 JSON 对象生成嵌套 record")
    void generatesNestedRecord() {
        final String code = converter.convert("{\"addr\":{\"city\":\"bj\"}}");
        assertAll(
                () -> assertTrue(code.contains("public record Dummy(Addr addr)"), "嵌套对象应作为 record 组件"),
                () -> assertTrue(code.contains("public record Addr(String city)"), "嵌套 record 应使用紧凑构造语法")
        );
    }

    @Test
    @DisplayName("正常：数组字段生成 List 泛型组件并导入 java.util.List")
    void generatesListComponentWithImport() {
        final String code = converter.convert("{\"tags\":[\"a\"]}");
        assertAll(
                () -> assertTrue(code.contains("List<String> tags"), "字符串数组应映射为 List<String> 组件"),
                () -> assertTrue(code.contains("import java.util.List;"), "存在 List 组件时应导入 java.util.List")
        );
    }

    @Test
    @DisplayName("边界：数组 JSON 根取首个对象元素生成 record")
    void takesFirstElementOfJsonArray() {
        final String code = converter.convert("[{\"a\":1}]");
        assertTrue(code.contains("Integer a"), "jsonToObject 应取数组首个元素生成 record 组件");
    }

    @Test
    @DisplayName("边界：空 JSON 对象生成无组件的 record")
    void generatesEmptyRecordForEmptyObject() {
        assertTrue(converter.convert("{}").contains("public record Dummy()"), "空对象应生成无组件的 record");
    }

    @Test
    @DisplayName("异常：非法 JSON 转换时抛出 JSONException")
    void throwsOnInvalidJson() {
        assertThrows(JSONException.class, () -> converter.convert("not a json"),
                "JSON.parse 对非法输入抛出 fastjson2 JSONException");
    }

    @Test
    @DisplayName("默认：reverseConvert 未覆写，原样返回输入")
    void reverseConvertReturnsInputAsIs() {
        final String any = "record Dummy() {}";
        assertEquals(any, converter.reverseConvert(any), "RecordConverter 未覆写 reverseConvert，走接口默认实现原样返回");
    }
}
