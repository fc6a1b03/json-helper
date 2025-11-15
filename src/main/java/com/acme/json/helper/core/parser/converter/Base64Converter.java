package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.json.JsonFormatter;

/**
 * BASE64转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class Base64Converter implements DataFormatConverter {
    @Override
    public String convert(final String json) throws ConvertException {
        return Base64.encode(json);
    }

    @Override
    public String reverseConvert(final String any) {
        return new JsonFormatter().process(Base64.decodeStr(any));
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.BASE64.equals(any);
    }
}