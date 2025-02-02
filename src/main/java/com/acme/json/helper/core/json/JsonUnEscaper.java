package com.acme.json.helper.core.json;

import com.alibaba.fastjson2.JSON;

/**
 * JSON去转义
 * @author 拒绝者
 * @date 2025-01-19
 */
public final class JsonUnEscaper implements JsonOperation {
    @Override
    public String process(final String json) {
        try {
            return new JsonFormatter().process(JSON.parseObject(json, String.class)).trim();
        } catch (Exception ignored) {
            return json;
        }
    }
}