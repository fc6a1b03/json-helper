package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

/**
 * TOML转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class TomlConverter implements DataFormatConverter {
    /**
     * TOML转换器
     */
    private static final TomlMapper toml = new TomlMapper();

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
    public boolean support(final AnyFile any) {
        return AnyFile.TOML.equals(any);
    }

    @Override
    public String convert(final String json) throws ConvertException {
        try {
            return toml.writeValueAsString(parse(json));
        } catch (final Exception e) {
            return "";
        }
    }
}