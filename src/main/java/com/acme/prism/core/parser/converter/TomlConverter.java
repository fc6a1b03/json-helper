package com.acme.prism.core.parser.converter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.toml.TomlMapper;

/**
 * TOML转换器
 *
 * @author 拒绝者
 * @date 2025-04-21
 */
public class TomlConverter implements DataFormatConverter {
    /**
     * 数组根节点占位名
     */
    private static final String ROOT_NAME = "dummy";
    /**
     * JSON 映射器（线程安全，构建一次全局复用）
     */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    /**
     * TOML 映射器（线程安全，构建一次全局复用）
     */
    private static final TomlMapper TOML_MAPPER = TomlMapper.builder().build();

    /**
     * 对作语法分析
     * @param json 数据
     * @return {@link JSONObject }
     */
    private static Object parse(final String json) {
        return switch (JSON.parse(json)) {
            case final JSONObject obj -> obj;
            case final JSONArray arr -> JSONObject.of(ROOT_NAME, arr);
            default -> JSONObject.of();
        };
    }

    @Override
    public String reverseConvert(final String any) {
        return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(TOML_MAPPER.readTree(any));
    }

    @Override
    public String convert(final String json) {
        return TOML_MAPPER.writeValueAsString(parse(json));
    }
}
