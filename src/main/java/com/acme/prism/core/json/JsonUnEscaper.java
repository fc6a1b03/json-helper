package com.acme.prism.core.json;

import com.alibaba.fastjson2.JSON;

/**
 * JSON去转义
 * @author 拒绝者
 * @date 2025-01-19
 */
public final class JsonUnEscaper implements JsonOperation {
    /**
     * JSON 格式化器（无状态，全局复用）
     */
    private static final JsonFormatter JSON_FORMATTER = new JsonFormatter();

    @Override
    public String process(final String json) {
        try {
            return JSON_FORMATTER.process(JSON.parseObject(json, String.class)).trim();
        } catch (Exception ignored) {
            return json;
        }
    }
}