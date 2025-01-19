package com.acme.json.helper.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.burt.jmespath.jackson.JacksonRuntime;

/**
 * JSON搜索引擎
 * @author 拒绝者
 * @date 2025-01-18
 */
public final class JsonSearchEngine implements JsonOperation {
    private final JacksonRuntime jmespath;

    public JsonSearchEngine() {
        this.jmespath = new JacksonRuntime();
    }

    /**
     * 搜索
     * @param json       json
     * @param expression 表达
     * @return {@link String }
     */
    @Override
    public String process(final String json, final String expression) {
        try {
            // JsonPath
            if (expression.startsWith("$")) {
                return JsonPath.read(json, expression);
            }
            // JMESPath
            else {
                return jmespath.compile(expression).search(new ObjectMapper().valueToTree(json)).asText();
            }
        } catch (Exception e) {
            return "";
        }
    }
}