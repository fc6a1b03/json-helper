package com.acme.json.helper.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON压缩器
 * @author 拒绝者
 * @date 2025-01-18
 */
public final class JsonCompressor implements JsonOperation {
    private final ObjectMapper mapper;

    public JsonCompressor() {
        this.mapper = new ObjectMapper()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, Boolean.FALSE);
    }

    @Override
    public String process(final String json) {
        try {
            return mapper.writeValueAsString(mapper.readTree(json));
        } catch (Exception e) {
            return json;
        }
    }
}