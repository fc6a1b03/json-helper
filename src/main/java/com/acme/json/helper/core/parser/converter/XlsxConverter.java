package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Xlsx转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class XlsxConverter extends TableStructure {
    /**
     * 标准化为数组
     * @param json 数据
     * @return {@link JSONArray }
     */
    private static JSONArray normalizeToArray(final String json) {
        return JSON.parse(json) instanceof final JSONArray array ?
                array : new JSONArray().fluentAdd(JSONObject.parse(json));
    }

    @Override
    public String convert(final String json) throws ConvertException {
        try {
            // 原始数据集合
            final List<Map<String, String>> data = flattenJsonArray(normalizeToArray(json));
            // 表头信息
            final String[] headers = data.stream()
                    .<JSONObject>mapMulti((item, consumer) -> consumer.accept(JSONObject.from(item)))
                    .flatMap(obj -> collectFlatHeaders(obj, ""))
                    .collect(Collectors.toCollection(LinkedHashSet::new))
                    .toArray(String[]::new);
            return JSONObject.of(
                    "headers", headers,
                    "data",
                    data.stream()
                            .<JSONObject>mapMulti((item, consumer) -> consumer.accept(JSONObject.from(item)))
                            .map(jsonObj -> Arrays.stream(headers)
                                    .map(jsonObj::get)
                                    .toArray(Object[]::new))
                            .toArray(Object[][]::new)
            ).toJSONString();
        } catch (final Exception e) {
            return "";
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.XLSX.equals(any);
    }

    /**
     * 收集扁平表头
     * @param obj       对象
     * @param parentKey 父密钥
     * @return {@link Stream }<{@link String }>
     */
    private Stream<String> collectFlatHeaders(final JSONObject obj, final String parentKey) {
        return obj.keySet().stream().mapMulti((key, consumer) -> {
            final String fullKey = StrUtil.isEmpty(parentKey) ? key : "%s_%s".formatted(parentKey, key);
            if (obj.get(key) instanceof final JSONObject nestedObj) {
                collectFlatHeaders(nestedObj, fullKey).forEach(consumer);
            } else {
                consumer.accept(fullKey);
            }
        });
    }
}