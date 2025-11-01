package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.felipestanzani.jtoon.JToon;

/**
 * TOON转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class ToonConverter implements DataFormatConverter {
    @Override
    public String convert(final String json) throws ConvertException {
        return JToon.encodeJson(json);
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.TOON.equals(any);
    }
}