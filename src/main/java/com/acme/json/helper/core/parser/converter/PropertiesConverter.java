package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;

/**
 * Properties转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class PropertiesConverter implements DataFormatConverter {
    /**
     * properties转换器
     */
    private static final JavaPropsMapper properties = new JavaPropsMapper();
    /** 对象转换器 */
    private static final ObjectMapper object = new ObjectMapper();

    @Override
    public String convert(final String json) throws ConvertException {
        try {
            return properties.writeValueAsString(JSON.parse(json));
        } catch (final Exception e) {
            return "";
        }
    }

    @Override
    public String reverseConvert(final String any) {
        try {
            return object.writerWithDefaultPrettyPrinter().writeValueAsString(properties.readTree(any));
        } catch (final JsonProcessingException e) {
            return any;
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.PROPERTIES.equals(any);
    }
}