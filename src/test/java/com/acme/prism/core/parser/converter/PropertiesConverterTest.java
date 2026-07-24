package com.acme.prism.core.parser.converter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Properties 转换器单元测试
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
class PropertiesConverterTest {

    private final PropertiesConverter converter = new PropertiesConverter();

    @Test
    @DisplayName("正常：JSON 对象转换为 properties 键值对")
    void convertsJsonObjectToProperties() {
        final String props = converter.convert("{\"name\":\"tom\",\"age\":18}");
        assertAll(
                () -> assertTrue(props.contains("name=tom"), "properties 输出应包含字符串键值对"),
                () -> assertTrue(props.contains("age=18"), "properties 输出应包含数字键值对")
        );
    }

    @Test
    @DisplayName("边界：嵌套 JSON 对象扁平化为点分隔键")
    void flattensNestedJsonWithDotKeys() {
        final String props = converter.convert("{\"db\":{\"host\":\"localhost\",\"port\":3306}}");
        assertAll(
                () -> assertTrue(props.contains("db.host=localhost"), "嵌套对象应扁平化为 db.host 键"),
                () -> assertTrue(props.contains("db.port=3306"), "嵌套对象应扁平化为 db.port 键")
        );
    }

    @Test
    @DisplayName("异常：非法 JSON 输入返回空串而非抛异常")
    void returnsEmptyStringOnInvalidJson() {
        assertEquals("", converter.convert("not a json"), "解析失败时 convert 吞掉异常并返回空串");
    }

    @Test
    @DisplayName("正常：properties 反向转换为 JSON，值全部为字符串")
    void reverseConvertsPropertiesToJson() {
        final String json = converter.reverseConvert("name=tom\nage=18\n");
        final JSONObject obj = JSON.parseObject(json);
        assertAll(
                () -> assertEquals("tom", obj.getString("name"), "properties 字符串值应保持为字符串"),
                () -> assertEquals("18", obj.getString("age"), "properties 无类型系统，数字值应为字符串")
        );
    }

    @Test
    @DisplayName("边界：点分隔键反向转换为嵌套 JSON 对象")
    void reverseConvertsDotKeysToNestedJson() {
        final String json = converter.reverseConvert("db.host=localhost\n");
        assertEquals("localhost", JSON.parseObject(json).getJSONObject("db").getString("host"),
                "点分隔键应反序列化为嵌套 JSON 对象");
    }

    @Test
    @DisplayName("边界：空 properties 反向转换为合法 JSON")
    void reverseConvertsEmptyProperties() {
        final String json = converter.reverseConvert("");
        assertTrue(JSON.isValid(json), "空 properties 反向转换结果应为合法 JSON");
    }

    @Test
    @DisplayName("往返：字符串字段 JSON→properties→JSON 数据等价")
    void roundTripPreservesStringData() {
        final String props = converter.convert("{\"name\":\"tom\"}");
        final JSONObject obj = JSON.parseObject(converter.reverseConvert(props));
        assertEquals("tom", obj.getString("name"), "properties 全程为字符串，字符串字段往返应保持等价");
    }
}
