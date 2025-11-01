package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import cn.hutool.core.lang.Opt;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * CSV转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class CsvConverter extends TableStructure {
    /**
     * 标准化为数组
     * @param json 数据
     * @return {@link JSONArray }
     */
    private static JSONArray normalizeToArray(final String json) {
        return Opt.of(JSON.parse(json))
                .filter(JSONArray.class::isInstance)
                .map(JSONArray.class::cast)
                .orElseGet(() -> JSONArray.of().fluentAdd(JSONObject.parse(json)));
    }

    /**
     * 提取表头
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

    @Override
    public String convert(final String json) throws ConvertException {
        try {
            final JSONArray data = normalizeToArray(json);
            return CsvMapper.builder().build().writer(
                    CsvSchema.builder()
                            .addColumns(extractColumns(data))
                            .build()
                            .withHeader()
            ).writeValueAsString(
                    flattenJsonArray(data)
            );
        } catch (final Exception e) {
            return "";
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.CSV.equals(any);
    }
}