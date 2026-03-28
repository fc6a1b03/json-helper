package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.Convert;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.*;

/**
 * 表格结构
 *
 * @author 拒绝者
 * @date 2025-04-21
 */
public abstract class TableStructure implements DataFormatConverter {
    /**
     * 展平JSON对象
     *
     * @param obj     对象
     * @param prefix  前缀
     * @param flatMap 平面地图
     */
    protected static void flattenJsonObject(final JSONObject obj, final String prefix, final Map<String, String> flatMap) {
        for (final Map.Entry<String, Object> entry : obj.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            final String fullKey = prefix.isEmpty() ? key : prefix + "_" + key;
            if (value instanceof JSONObject) {
                flattenJsonObject((JSONObject) value, fullKey, flatMap);
            } else {
                flatMap.put(fullKey, Convert.toStr(value));
            }
        }
    }

    /**
     * 展平JSON数组
     *
     * @param jsonArray json数组
     * @return {@link List }<{@link Map }<{@link String }, {@link String }>>
     */
    protected static List<Map<String, String>> flattenJsonArray(final JSONArray jsonArray) {
        final List<Map<String, String>> rows = new ArrayList<>(jsonArray.size());
        for (final Object item : jsonArray) {
            final Map<String, String> flatMap = new LinkedHashMap<>();
            flattenJsonObject((JSONObject) item, "", flatMap);
            rows.add(flatMap);
        }
        return rows;
    }

    /**
     * 收集扁平表头
     *
     * @param obj     对象
     * @param prefix  前缀
     * @param headers 标头
     */
    protected static void collectFlatHeaders(final JSONObject obj, final String prefix, final LinkedHashSet<String> headers) {
        for (final Map.Entry<String, Object> entry : obj.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            final String fullKey = prefix.isEmpty() ? key : "%s_%s".formatted(prefix, key);
            if (value instanceof JSONObject) {
                collectFlatHeaders((JSONObject) value, fullKey, headers);
            } else {
                headers.add(fullKey);
            }
        }
    }
}
