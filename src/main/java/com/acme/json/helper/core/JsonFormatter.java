package com.acme.json.helper.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

/**
 * JSON格式化程序
 * @author 拒绝者
 * @date 2025-01-18
 */
public final class JsonFormatter implements JsonOperation {
    @Override
    public String process(final Object input) {
        return JSON.toJSONString(input, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteMapNullValue).trim();
    }

    @Override
    public String process(final String json) {
        try {
            return JSON.toJSONString(JSON.parse(json), JSONWriter.Feature.PrettyFormat).trim();
        } catch (Exception e) {
            return json;
        }
    }
}