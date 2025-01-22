package com.acme.json.helper.core;

import com.alibaba.fastjson2.JSON;

/**
 * JSON转义
 * @author 拒绝者
 * @date 2025-01-19
 */
public final class JsonEscaper implements JsonOperation {
    @Override
    public String process(final String json) {
        try {
            return JSON.toJSONString(json).trim();
        } catch (Exception e) {
            return json;
        }
    }
}