package com.acme.json.helper.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONPath;
import com.alibaba.fastjson2.JSONWriter;

import java.util.Optional;

/**
 * JSON搜索引擎
 * @author 拒绝者
 * @date 2025-01-18
 */
public final class JsonSearchEngine implements JsonOperation {
    /**
     * 搜索
     * @param json       json
     * @param expression 表达
     * @return {@link String }
     */
    @Override
    public String process(final String json, final String expression) {
        try {
            return Optional.ofNullable(JSON.toJSONString(JSONPath.eval(json, expression), JSONWriter.Feature.PrettyFormat))
                    .filter(item -> Boolean.FALSE.equals("null".equals(item)))
                    .orElse("").trim();
        } catch (Exception e) {
            return "";
        }
    }
}