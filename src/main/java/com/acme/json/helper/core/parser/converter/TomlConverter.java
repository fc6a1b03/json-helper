package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.toml.TomlMapper;

/**
 * TOML转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class TomlConverter implements DataFormatConverter {
    /**
     * 对作语法分析
     * @param json 数据
     * @return {@link JSONObject }
     */
    private static Object parse(final String json) {
        return switch (JSON.parse(json)) {
            case final JSONObject obj -> obj;
            case final JSONArray arr -> JSONObject.of("dummy", arr);
            default -> new JSONObject();
        };
    }

    @Override
    public String reverseConvert(final String any) {
        return JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(TomlMapper.builder().build().readTree(any));
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.TOML.equals(any);
    }

    @Override
    public String convert(final String json) throws ConvertException {
        return TomlMapper.builder().build().writeValueAsString(parse(json));
    }
}