package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import cn.hutool.core.lang.Opt;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.Objects;

/**
 * 数据格式变换器
 * @author 拒绝者
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
     * 反向转换
     * @param any 任何
     * @return {@link String }
     */
    default String reverseConvert(final String any) {
        return any;
    }

    /**
     * 支持
     * @param any 任何
     * @return boolean
     */
    boolean support(final AnyFile any);

    /**
     * JSON到对象
     * @param json 数据
     * @return {@link JSONObject }
     */
    static JSONObject jsonToObject(final String json) {
        return switch (JSON.parse(json)) {
            case final JSONObject obj -> obj;
            case final JSONArray arr -> Opt.ofEmptyAble(arr)
                    .map(ArrayList::getFirst).filter(Objects::nonNull)
                    .filter(JSONObject.class::isInstance).map(JSONObject.class::cast)
                    .orElseGet(JSONObject::of);
            default -> JSONObject.of();
        };
    }
}