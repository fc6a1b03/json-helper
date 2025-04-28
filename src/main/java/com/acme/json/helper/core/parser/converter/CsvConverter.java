package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import cn.hutool.core.lang.Opt;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CSV转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class CsvConverter implements DataFormatConverter {
    /**
     * CSV转换器
     */
    private static final CsvMapper csv = new CsvMapper();

    /**
     * 标准化为数组
     * @param json 数据
     * @return {@link JSONArray }
     */
    private static JSONArray normalizeToArray(final String json) {
        return Opt.of(JSON.parse(json))
                .filter(JSONArray.class::isInstance)
                .map(JSONArray.class::cast)
                .orElseGet(() -> new JSONArray().fluentAdd(JSONObject.parse(json)));
    }

    /**
     * 提字段
     * @param jsonArray json数组
     * @return {@link LinkedHashSet }<{@link CsvSchema.Column }>
     */
    private static LinkedHashSet<CsvSchema.Column> extractColumns(final JSONArray jsonArray) {
        final LinkedHashSet<String> flatHeaders = new LinkedHashSet<>();
        jsonArray.stream()
                .map(JSONObject.class::cast)
                .forEach(obj -> collectFlatHeaders(obj, "", flatHeaders));
        return flatHeaders.stream()
                .map(fieldName -> new CsvSchema.Column(0, fieldName, CsvSchema.ColumnType.STRING))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 收集扁平表头
     * @param obj     对象
     * @param prefix  前缀
     * @param headers 标头
     */
    private static void collectFlatHeaders(final JSONObject obj, final String prefix, final LinkedHashSet<String> headers) {
        obj.forEach((key, value) -> {
            final String fullKey = prefix.isEmpty() ? key : "%s_%s".formatted(prefix, key);
            if (value instanceof JSONObject) {
                collectFlatHeaders((JSONObject) value, fullKey, headers);
            } else {
                headers.add(fullKey);
            }
        });
    }

    /**
     * 展平JSON对象
     * @param obj     对象
     * @param prefix  前缀
     * @param flatMap 平面地图
     */
    private static void flattenJsonObject(final JSONObject obj, final String prefix, final Map<String, String> flatMap) {
        obj.forEach((key, value) -> {
            final String fullKey = prefix.isEmpty() ? key : prefix + "_" + key;
            if (value instanceof JSONObject) {
                flattenJsonObject((JSONObject) value, fullKey, flatMap);
            } else {
                flatMap.put(fullKey, value.toString());
            }
        });
    }

    @Override
    public String convert(final String json) throws ConvertException {
        try {
            final JSONArray jsonArray = normalizeToArray(json);
            return csv.writer(
                    CsvSchema.builder()
                            .addColumns(extractColumns(jsonArray))
                            .build()
                            .withHeader()
            ).writeValueAsString(
                    flattenJsonArray(jsonArray)
            );
        } catch (final Exception e) {
            return "";
        }
    }

    /**
     * 展平JSON数组
     * @param jsonArray json数组
     * @return {@link List }<{@link Map }<{@link String }, {@link String }>>
     */
    private List<Map<String, String>> flattenJsonArray(final JSONArray jsonArray) {
        return jsonArray.stream()
                .map(JSONObject.class::cast)
                .map(obj -> {
                    final Map<String, String> flatMap = new LinkedHashMap<>();
                    flattenJsonObject(obj, "", flatMap);
                    return flatMap;
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.CSV.equals(any);
    }
}