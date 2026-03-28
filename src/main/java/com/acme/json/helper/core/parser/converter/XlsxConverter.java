package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Xlsx转换器
 *
 * @author 拒绝者
 * @date 2025-04-21
 */
public class XlsxConverter extends TableStructure {
    /**
     * 标准化为数组
     *
     * @param json 数据
     * @return {@link JSONArray }
     */
    private static JSONArray normalizeToArray(final String json) {
        return JSON.parse(json) instanceof final JSONArray array ?
                array : JSONArray.of().fluentAdd(JSONObject.parse(json));
    }

    @Override
    public String convert(final String json) throws ConvertException {
        try {
            // 原始数据集合
            final List<Map<String, String>> data = flattenJsonArray(normalizeToArray(json));
            final LinkedHashSet<String> headerSet = new LinkedHashSet<>();
            for (final Map<String, String> row : data) {
                headerSet.addAll(row.keySet());
            }
            final String[] headers = headerSet.toArray(String[]::new);
            final Object[][] rows = new Object[data.size()][headers.length];
            for (int rowIndex = 0; rowIndex < data.size(); rowIndex++) {
                final Map<String, String> row = data.get(rowIndex);
                for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
                    rows[rowIndex][columnIndex] = row.get(headers[columnIndex]);
                }
            }
            return JSONObject.of(
                    "headers", headers,
                    "data", rows
            ).toJSONString();
        } catch (final Exception e) {
            return "";
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.XLSX.equals(any);
    }
}
