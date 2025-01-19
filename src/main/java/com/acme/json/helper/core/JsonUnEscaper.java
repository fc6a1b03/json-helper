package com.acme.json.helper.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON去转义
 * @author 拒绝者
 * @date 2025-01-19
 */
public final class JsonUnEscaper implements JsonOperation {
    private final ObjectMapper mapper;

    public JsonUnEscaper() {
        this.mapper = new ObjectMapper()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, Boolean.FALSE);
    }

    @Override
    public String process(final String json) {
        try {
            return mapper.readValue(json, JsonWrapper.class).json();
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * JSON辅助包装器
     * @author 拒绝者
     * @date 2025-01-19
     */
    private record JsonWrapper(String json) {
    }
}