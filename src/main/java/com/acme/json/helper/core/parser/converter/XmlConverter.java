package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * XML转换器
 *
 * @author xuhaifeng
 * @date 2025-04-21
 */
public class XmlConverter implements DataFormatConverter {
    /**
     * XML转换器
     */
    private static final XmlMapper xml = new XmlMapper();

    @Override
    public String convert(final String json) throws ConvertException {
        try {
            return xml.writerWithDefaultPrettyPrinter().withRootName("dummy").writeValueAsString(JSON.parseObject(json));
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.XML.equals(any);
    }
}