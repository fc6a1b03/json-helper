package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

/**
 * TOML转换器
 *
 * @author xuhaifeng
 * @date 2025-04-21
 */
public class TomlConverter implements DataFormatConverter {
    /**
     * TOML转换器
     */
    private static final TomlMapper toml = new TomlMapper();

    @Override
    public String convert(final String json) throws ConvertException {
        try {
            return toml.writeValueAsString(JSON.parseObject(json));
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.TOML.equals(any);
    }
}