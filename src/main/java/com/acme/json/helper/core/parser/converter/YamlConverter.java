package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * YAML转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class YamlConverter implements DataFormatConverter {
    /**
     * YAML转换器
     */
    private static final YAMLMapper yaml = new YAMLMapper();
    /** 对象转换器 */
    private static final ObjectMapper object = new ObjectMapper();

    @Override
    public String convert(final String json) throws ConvertException {
        try {
            return yaml.writeValueAsString(JSON.parse(json));
        } catch (final JsonProcessingException e) {
            return "";
        }
    }

    @Override
    public String reverseConvert(final String any) {
        try {
            return object.writerWithDefaultPrettyPrinter().writeValueAsString(yaml.readTree(any));
        } catch (final JsonProcessingException e) {
            return any;
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.YAML.equals(any);
    }
}