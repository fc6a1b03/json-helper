package com.acme.json.helper.core.parser;

import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.parser.converter.*;

import java.util.List;

/**
 * JSON转任何文件
 * @see AnyFile
 * @author 拒绝者
 * @date 2025-01-26
 */
public class JsonParser {
    /**
     * 转换器
     */
    private static final List<DataFormatConverter> CONVERTERS = List.of(
            new XmlConverter(),
            new YamlConverter(),
            new TomlConverter(),
            new PropertiesConverter(),
            new UrlParamsConverter(),
            new ClassConverter(),
            new RecordConverter()
    );

    /**
     * 转换器
     * @param json 数据
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
}