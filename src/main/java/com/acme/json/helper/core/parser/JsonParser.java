package com.acme.json.helper.core.parser;

import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.parser.converter.*;

import java.util.List;
import java.util.Objects;

/**
 * JSON转任何文件
 * @author 拒绝者
 * @date 2025-01-26
 * @see AnyFile
 */
public class JsonParser {
    /**
     * 转换器
     */
    private static final List<DataFormatConverter> CONVERTERS = List.of(
            new XmlConverter(),
            new CsvConverter(),
            new YamlConverter(),
            new TomlConverter(),
            new XlsxConverter(),
            new ClassConverter(),
            new RecordConverter(),
            new UrlParamsConverter(),
            new PropertiesConverter()
    );

    /**
     * 转换器
     * @param json         数据
     * @param targetFormat 目标格式
     * @return {@link String }
     */
    public static String convert(final String json, final AnyFile targetFormat) {
        return CONVERTERS.stream()
                .filter(c -> c.support(targetFormat))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的格式"))
                .convert(json);
    }

    /**
     * 反向转换
     *
     * @param json 数据
     * @param targetFormat 目标格式
     * @return {@link String }
     */
    public static String reverseConvert(final String json, final AnyFile targetFormat) {
        if (Objects.isNull(targetFormat)) {
            return "";
        }
        return CONVERTERS.stream()
                .filter(c -> c.support(targetFormat))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的格式"))
                .reverseConvert(json);
    }
}