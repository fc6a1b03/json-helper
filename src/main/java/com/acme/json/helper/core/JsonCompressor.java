package com.acme.json.helper.core;

import com.alibaba.fastjson2.JSON;

/**
 * JSON压缩器
 * @author 拒绝者
 * @date 2025-01-18
 */
public final class JsonCompressor implements JsonOperation {
    @Override
    public String process(final String json) {
        try {
            return JSON.toJSONString(JSON.parse(json)).trim();
        } catch (Exception e) {
            return json;
        }
    }
}