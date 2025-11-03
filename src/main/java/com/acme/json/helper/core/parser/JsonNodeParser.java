package com.acme.json.helper.core.parser;

import cn.hutool.core.lang.Opt;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * JSON节点解析器
 * @author 拒绝者
 * @date 2025-01-28
 */
public class JsonNodeParser {
    /**
     * 解析
     * @param key  钥匙
     * @param json json
     * @return {@link JsonNode }
     */
    public static JsonNode parse(final String key, final String json) {
        return parseNode(key,
                Opt.of(JSON.isValid(json)).filter(i -> i)
                        .map(item -> JSON.parse(json)).orElse(json)
        );
    }

    /**
     * 解析节点
     * @param key   钥匙
     * @param value 价值
     * @return {@link JsonNode }
     */
    private static JsonNode parseNode(final String key, final Object value) {
        return switch (value) {
            case final JSONObject obj -> new JsonNode(
                    key,
                    obj,
                    obj.keySet().stream()
                            .map(k -> parseNode(k, obj.get(k)))
                            .toList()
            );
            case final JSONArray arr -> new JsonNode(
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
     * @author 拒绝者
     * @date 2025-01-28
     */
    public record JsonNode(String key, Object value, List<JsonNode> children) {
        @Override
        public @NotNull String toString() {
            return Opt.ofNullable(this.value)
                    .map(item -> "{\"%s\": %s}".formatted(this.key, this.value()))
                    .orElse("");
        }

        /**
         * 值
         * @return {@link String }
         */
        public Object value() {
            return Opt.ofNullable(this.value)
                    .map(JSON::toJSONString)
                    .orElse("");
        }

        /**
         * 类型
         * @return {@link String }
         */
        public String type() {
            return switch (this.value) {
                case null -> "";
                case final JSONObject ignored -> "Object";
                case final JSONArray ignored -> "Array";
                default -> this.value.getClass().getSimpleName();
            };
        }
    }
}