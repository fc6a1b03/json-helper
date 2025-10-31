package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.javaprop.JavaPropsMapper;

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
        return JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(properties.readTree(any));
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.PROPERTIES.equals(any);
    }
}