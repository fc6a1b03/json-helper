package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.json.JsonFormatter;
import dev.toonformat.jtoon.DecodeOptions;
import dev.toonformat.jtoon.Delimiter;
import dev.toonformat.jtoon.EncodeOptions;
import dev.toonformat.jtoon.JToon;
import dev.toonformat.jtoon.KeyFolding;
import dev.toonformat.jtoon.PathExpansion;

/**
 * TOON 转换器。
 *
 * @author 拒绝者
 * @date 2025-04-21
 */
public class ToonConverter implements DataFormatConverter {
    private static final JsonFormatter JSON_FORMATTER = new JsonFormatter();
    private static final DecodeOptions STRICT_DECODE_OPTIONS = new DecodeOptions(2, Delimiter.TAB, Boolean.TRUE, PathExpansion.SAFE);
    private static final DecodeOptions RELAXED_DECODE_OPTIONS = new DecodeOptions(2, Delimiter.TAB, Boolean.FALSE, PathExpansion.SAFE);
    private static final EncodeOptions ENCODE_OPTIONS = new EncodeOptions(2, Delimiter.TAB, Boolean.FALSE, KeyFolding.OFF, Integer.MAX_VALUE);

    @Override
    public String convert(final String json) throws ConvertException {
        return JToon.encodeJson(json, ENCODE_OPTIONS);
    }

    @Override
    public String reverseConvert(final String any) {
        try {
            return JSON_FORMATTER.process(JToon.decodeToJson(any, STRICT_DECODE_OPTIONS));
        } catch (final Exception ignored) {
            try {
                return JSON_FORMATTER.process(JToon.decodeToJson(any, RELAXED_DECODE_OPTIONS));
            } catch (final Exception fallbackIgnored) {
                return any;
            }
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.TOON.equals(any);
    }
}