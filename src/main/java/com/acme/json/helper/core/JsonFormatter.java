package com.acme.json.helper.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON格式化程序
 * @author 拒绝者
 * @date 2025-01-18
 */
public final class JsonFormatter implements JsonOperation {
    private final ObjectMapper mapper;

    public JsonFormatter() {
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, Boolean.FALSE);
    }

    @Override
    public String process(final String json) {
        try {
            return mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(mapper.readTree(json));
        } catch (Exception e) {
            return json;
        }
    }
}