package com.acme.json.helper.core.parser.converter;

import com.alibaba.fastjson2.JSONException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java 类转换器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class ClassConverterTest {

    private final ClassConverter converter = new ClassConverter();

    @Test
    @DisplayName("正常：JSON 对象生成带 lombok.Data 的 Java 类源码")
    void generatesClassWithLombokData() {
        final String code = converter.convert("{\"name\":\"tom\",\"age\":18}");
        assertAll(
                () -> assertTrue(code.contains("import lombok.Data;"), "类源码应包含 lombok.Data 导入"),
                () -> assertTrue(code.contains("@Data public class Dummy {"), "类源码应包含 @Data 注解与默认类名 Dummy"),
                () -> assertTrue(code.contains("private String name;"), "字符串字段应映射为 String"),
                () -> assertTrue(code.contains("private Integer age;"), "整数字段应映射为 Integer")
        );
    }

    @Test
    @DisplayName("正常：嵌套 JSON 对象生成 static 嵌套类")
    void generatesStaticNestedClass() {
        final String code = converter.convert("{\"addr\":{\"city\":\"bj\"}}");
        assertAll(
                () -> assertTrue(code.contains("private Addr addr;"), "嵌套对象字段类型应为大驼峰类名"),
                () -> assertTrue(code.contains("public static class Addr {"), "嵌套类应带 static 修饰符"),
                () -> assertTrue(code.contains("private String city;"), "嵌套类应包含自身字段")
        );
    }

    @Test
    @DisplayName("正常：数组字段生成 List 泛型并导入 java.util.List")
    void generatesListGenericFieldWithImport() {
        final String code = converter.convert("{\"tags\":[\"a\",\"b\"],\"scores\":[1,2]}");
        assertAll(
                () -> assertTrue(code.contains("private List<String> tags;"), "字符串数组应映射为 List<String>"),
                () -> assertTrue(code.contains("private List<Integer> scores;"), "整数数组应映射为 List<Integer>"),
                () -> assertTrue(code.contains("import java.util.List;"), "存在 List 字段时应导入 java.util.List")
        );
    }

    @Test
    @DisplayName("正常：对象数组字段生成 List<嵌套类> 泛型")
    void generatesListOfNestedClass() {
        final String code = converter.convert("{\"items\":[{\"id\":1}]}");
        assertAll(
                () -> assertTrue(code.contains("private List<Items> items;"), "对象数组应映射为 List<Items>"),
                () -> assertTrue(code.contains("public static class Items {"), "数组元素对象应生成嵌套类"),
                () -> assertTrue(code.contains("private Integer id;"), "嵌套类应包含数组元素字段")
        );
    }

    @Test
    @DisplayName("边界：数组 JSON 根取首个对象元素生成类")
    void takesFirstElementOfJsonArray() {
        final String code = converter.convert("[{\"a\":1},{\"b\":2}]");
        assertAll(
                () -> assertTrue(code.contains("private Integer a;"), "jsonToObject 应取数组首个元素"),
                () -> assertFalse(code.contains("private Integer b;"), "数组后续元素的字段不应出现")
        );
    }

    @Test
    @DisplayName("边界：空数组字段映射为 List<Object>")
    void mapsEmptyArrayToListOfObject() {
        assertTrue(converter.convert("{\"tags\":[]}").contains("private List<Object> tags;"),
                "空数组字段应映射为 List<Object>");
    }

    @Test
    @DisplayName("边界：空 JSON 对象生成无字段的空类")
    void generatesEmptyClassForEmptyObject() {
        final String code = converter.convert("{}");
        assertAll(
                () -> assertTrue(code.contains("@Data public class Dummy {"), "空对象仍应生成类声明"),
                () -> assertFalse(code.contains("private"), "空对象不应生成任何字段")
        );
    }

    @Test
    @DisplayName("边界：布尔与 null 字段的类型映射")
    void mapsBooleanAndNullFields() {
        final String code = converter.convert("{\"active\":true,\"ext\":null}");
        assertAll(
                () -> assertTrue(code.contains("private Boolean active;"), "布尔字段应映射为 Boolean"),
                () -> assertTrue(code.contains("private Object ext;"), "null 字段应映射为 Object")
        );
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
        final String any = "class Dummy {}";
        assertEquals(any, converter.reverseConvert(any), "ClassConverter 未覆写 reverseConvert，走接口默认实现原样返回");
    }
}
