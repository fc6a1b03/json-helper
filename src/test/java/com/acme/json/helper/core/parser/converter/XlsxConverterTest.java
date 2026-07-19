package com.acme.json.helper.core.parser.converter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XLSX 转换器单元测试
 * <p>
 * 注意：XlsxConverter.convert 输出的是含 headers/data 两个键的 JSON 结构文本，
 * 并非真实的 xlsx 二进制文件内容。
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class XlsxConverterTest {

    private final XlsxConverter converter = new XlsxConverter();

    @Test
    @DisplayName("正常：对象数组转换为 headers/data 结构的 JSON 文本")
    void convertsObjectArrayToHeadersAndData() {
        final String result = converter.convert("[{\"name\":\"tom\",\"age\":18}]");
        assertTrue(JSON.isValidObject(result), "输出应为合法 JSON 对象文本而非 xlsx 二进制");
        final JSONObject obj = JSON.parseObject(result);
        final JSONArray headers = obj.getJSONArray("headers");
        final JSONArray data = obj.getJSONArray("data");
        assertAll(
                () -> assertEquals(JSONArray.of("name", "age"), headers, "headers 应为字段名数组"),
                () -> assertEquals(1, data.size(), "data 应包含一行"),
                () -> assertEquals(JSONArray.of("tom", "18"), data.getJSONArray(0),
                        "data 行的值全部展平为字符串")
        );
    }

    @Test
    @DisplayName("边界：单个 JSON 对象被包装为一行数据")
    void wrapsSingleObjectAsOneRow() {
        final JSONObject obj = JSON.parseObject(converter.convert("{\"a\":1}"));
        assertAll(
                () -> assertEquals(JSONArray.of("a"), obj.getJSONArray("headers"), "单对象包装后 headers 应仅含字段 a"),
                () -> assertEquals(JSONArray.of(JSONArray.of("1")), obj.getJSONArray("data"), "单对象包装后 data 应仅含一行值")
        );
    }

    @Test
    @DisplayName("边界：嵌套对象展平为下划线拼接的键")
    void flattensNestedObjectKeys() {
        final JSONObject obj = JSON.parseObject(converter.convert("{\"user\":{\"name\":\"tom\"}}"));
        assertAll(
                () -> assertEquals(JSONArray.of("user_name"), obj.getJSONArray("headers"), "嵌套对象展平后 headers 应仅含 user_name 键"),
                () -> assertEquals(JSONArray.of(JSONArray.of("tom")), obj.getJSONArray("data"), "嵌套对象展平后 data 应仅含嵌套字段值")
        );
    }

    @Test
    @DisplayName("边界：字段缺失的行对应位置为 null")
    void fillsNullForMissingFields() {
        final JSONArray data = JSON.parseObject(converter.convert("[{\"a\":1},{\"b\":2}]")).getJSONArray("data");
        assertAll(
                () -> assertNull(data.getJSONArray(0).get(1), "第一行缺失 b 字段应为 null"),
                () -> assertNull(data.getJSONArray(1).get(0), "第二行缺失 a 字段应为 null")
        );
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
        final String any = "whatever";
        assertEquals(any, converter.reverseConvert(any), "XlsxConverter 未覆写 reverseConvert，走接口默认实现原样返回");
    }
}
