package com.acme.json.helper.core.parser.converter;

import com.alibaba.fastjson2.JSON;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * XML转换器
 *
 * @author 拒绝者
 * @date 2025-04-21
 */
public class XmlConverter implements DataFormatConverter {
    /**
     * XML 根节点占位名
     */
    private static final String ROOT_NAME = "dummy";
    /**
     * JSON 映射器（线程安全，构建一次全局复用）
     */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    /**
     * XML 映射器（线程安全，构建一次全局复用）
     */
    private static final XmlMapper XML_MAPPER = XmlMapper.builder().build();

    @Override
    public String convert(final String json) {
        return XML_MAPPER.writerWithDefaultPrettyPrinter().withRootName(ROOT_NAME).writeValueAsString(JSON.parse(json));
    }

    @Override
    public String reverseConvert(final String any) {
        return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(XML_MAPPER.readTree(any));
    }
}
