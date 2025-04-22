package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.convert.ConvertException;
import cn.hutool.core.lang.Opt;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * URL参数转换器
 *
 * @author xuhaifeng
 * @date 2025-04-21
 */
public class UrlParamsConverter implements DataFormatConverter {
    @Override
    public String convert(final String json) throws ConvertException {
        final StringBuilder urlParams = new StringBuilder();
        JSON.parseObject(json).forEach((key, val) ->
                urlParams.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append("=").append(URLEncoder.encode(Convert.toStr(val), StandardCharsets.UTF_8)).append("&")
        );
        return Opt.ofBlankAble(urlParams)
                .map(item -> item.substring(0, item.length() - 1))
                .orElse("");
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.URL_PARAMS.equals(any);
    }
}