package com.acme.json.helper.core.parser;

import cn.hutool.core.lang.Opt;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * JSON节点解析器
 *
 * @author 拒绝者
 * @date 2025-01-28
 */
public class JsonNodeParser {
    /**
     * 解析
     *
     * @param key 钥匙
     * @param json json
     * @return {@link JsonNode }
     */
    public static JsonNode parse(final String key, final String json) {
        return parseNode(key, JSON.parse(json));
    }

    /**
     * 解析节点
     *
     * @param key 钥匙
     * @param value 价值
     * @return {@link JsonNode }
     */
    private static JsonNode parseNode(final String key, final Object value) {
        return switch (value) {
            case JSONObject obj -> new JsonNode(
                    key,
                    obj,
                    obj.keySet().stream()
                            .map(k -> parseNode(k, obj.get(k)))
                            .toList()
            );
            case JSONArray arr -> new JsonNode(
                    key,
                    arr,
                    IntStream.range(0, arr.size())
                            .mapToObj(i -> parseNode("[%d]".formatted(i), arr.get(i)))
                            .toList()
            );
            case null -> new JsonNode(key, null, Collections.emptyList());
            default -> new JsonNode(
                    key,
                    value,
                    Collections.emptyList()
            );
        };
    }

    /**
     * JSON节点
     *
     * @author xuhaifeng
     * @date 2025-01-28
     */
    public record JsonNode(String key, Object value, List<JsonNode> children) {
        @Override
        public String toString() {
            return Opt.ofNullable(value)
                    .map(item -> "{\"%s\": %s}".formatted(key, value()))
                    .orElse("");
        }

        /**
         * 值
         * @return {@link String }
         */
        public Object value() {
            return Opt.ofNullable(value)
                    .map(JSON::toJSONString)
                    .orElse("");
        }

        /**
         * 类型
         *
         * @return {@link String }
         */
        public String type() {
            return switch (value) {
                case null -> "";
                case JSONObject ignored -> "Object";
                case JSONArray ignored -> "Array";
                default -> value.getClass().getSimpleName();
            };
        }
    }
}