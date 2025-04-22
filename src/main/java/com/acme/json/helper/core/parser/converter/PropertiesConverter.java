package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;

/**
 * Properties转换器
 *
 * @author xuhaifeng
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
            return properties.writeValueAsString(JSON.parseObject(json));
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.PROPERTIES.equals(any);
    }
}