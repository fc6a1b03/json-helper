package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.codec.Base64;
import com.acme.json.helper.core.json.JsonFormatter;

/**
 * BASE64转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class Base64Converter implements DataFormatConverter {
    /**
     * JSON 格式化器（无状态，全局复用）
     */
    private static final JsonFormatter JSON_FORMATTER = new JsonFormatter();

    @Override
    public String convert(final String json) {
        return Base64.encode(json);
    }

    @Override
    public String reverseConvert(final String any) {
        return JSON_FORMATTER.process(Base64.decodeStr(any));
    }

}