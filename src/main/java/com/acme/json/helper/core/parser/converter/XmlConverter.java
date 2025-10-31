package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * XML转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class XmlConverter implements DataFormatConverter {
    @Override
    public String convert(final String json) throws ConvertException {
        return XmlMapper.builder().build().writerWithDefaultPrettyPrinter().withRootName("dummy").writeValueAsString(JSON.parse(json));
    }

    @Override
    public String reverseConvert(final String any) {
        return JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(XmlMapper.builder().build().readTree(any));
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.XML.equals(any);
    }
}