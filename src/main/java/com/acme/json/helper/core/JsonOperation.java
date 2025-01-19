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

    class JsonOperationException extends RuntimeException {
        /**
         * JSON操作异常
         * @param message 消息
         */
        public JsonOperationException(final String message) {
            super(message);
        }

        /**
         * JSON操作异常
         * @param message 消息
         * @param cause   原因
         */
        public JsonOperationException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}