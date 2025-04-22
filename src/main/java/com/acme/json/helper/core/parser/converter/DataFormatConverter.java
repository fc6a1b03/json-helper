package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;

/**
 * 数据格式变换器
 * @author xuhaifeng
 * @date 2025-04-21
 */
public interface DataFormatConverter {
    /**
     * 转换
     * @param json 数据
     * @return {@link String }
     * @throws ConvertException 转换异常
     */
    String convert(final String json) throws ConvertException;

    /**
     * 支持
     *
     * @param any 任何
     * @return boolean
     */
    boolean support(final AnyFile any);
}