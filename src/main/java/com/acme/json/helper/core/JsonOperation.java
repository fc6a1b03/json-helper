package com.acme.json.helper.core;

/**
 * JSON操作
 * @author 拒绝者
 * @date 2025-01-18
 */
public sealed interface JsonOperation permits JsonCompressor, JsonEscaper, JsonFormatter, JsonSearchEngine, JsonUnEscaper {
    /**
     * JSON操作
     * @param input 输入
     * @return {@link String }
     */
    default String process(final String input) {
        return input;
    }

    /**
     * JSON操作
     * @param input      输入
     * @param expression 表达
     * @return {@link String }
     */
    default String process(final String input, final String expression) {
        return process(input);
    }
}