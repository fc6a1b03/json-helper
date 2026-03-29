package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.json.JsonFormatter;
import com.alibaba.fastjson2.JSON;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayList;

/**
 * YAML 转换器。
 *
 * @author 拒绝者
 * @date 2025-04-21
 */
public class YamlConverter implements DataFormatConverter {
    private static final JsonFormatter JSON_FORMATTER = new JsonFormatter();
    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();
    @Override
    public String convert(final String json) throws ConvertException {
        return YAML_MAPPER.writeValueAsString(JSON.parse(json));
    }

    @Override
    public String reverseConvert(final String any) {
        try (final MappingIterator<Object> iterator = YAML_MAPPER.readerFor(Object.class).readValues(any)) {
            final ArrayList<Object> documents = new ArrayList<>();
            while (iterator.hasNextValue()) {
                documents.add(iterator.nextValue());
            }
            if (documents.isEmpty()) {
                return any;
            }
            return JSON_FORMATTER.process(documents.size() == 1 ? documents.getFirst() : documents);
        } catch (final Exception e) {
            return any;
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.YAML.equals(any);
    }
}