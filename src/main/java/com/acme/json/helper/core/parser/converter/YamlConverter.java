package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * YAML转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class YamlConverter implements DataFormatConverter {
    @Override
    public String convert(final String json) throws ConvertException {
        return YAMLMapper.builder().build().writeValueAsString(JSON.parse(json));
    }

    @Override
    public String reverseConvert(final String any) {
        return JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(YAMLMapper.builder().build().readTree(any));
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.YAML.equals(any);
    }
}