package com.acme.json.helper.core.json;

import cn.hutool.core.lang.Opt;
import com.alibaba.fastjson2.JSONPath;

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
            return Opt.ofBlankAble(new JsonFormatter().process(JSONPath.eval(json, expression).toString()))
                    .filter(item -> Boolean.FALSE.equals("null".equals(item)))
                    .orElse("").trim();
        } catch (Exception e) {
            return "";
        }
    }
}