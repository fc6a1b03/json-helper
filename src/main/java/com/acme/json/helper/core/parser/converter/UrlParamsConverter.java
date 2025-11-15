package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.convert.ConvertException;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.json.JsonFormatter;
import com.alibaba.fastjson2.JSON;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * URL参数转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class UrlParamsConverter implements DataFormatConverter {
    /**
     * 解析 URL 参数字符串为键值对映射
     * <p>
     * 将 URL 参数字符串按 &amp; 分割, 并进一步按 = 分割键值对, 解码后构建成键值对映射<br/>
     * 参数值为空时将被设置为空字符串.
     * @param urlParams URL 参数字符串, 格式如 "key1=value1&amp;key2=value2"
     * @return 解析后的参数映射, 键为参数名, 值为参数值
     */
    private static Map<String, Object> parseUrlParams(final String urlParams) {
        final Map<String, Object> params = new LinkedHashMap<>();
        Arrays.stream(urlParams.split("&", -1))
                .filter(pair -> !StrUtil.isEmpty(pair))
                .forEach(pair -> {
                    final String key, value;
                    final int idx = pair.indexOf('=');
                    if (idx == -1) {
                        value = "";
                        key = URLDecoder.decode(pair, StandardCharsets.UTF_8);
                    } else {
                        key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                        value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    }
                    // 智能识别并转换值类型
                    params.put(key, parseValue(value));
                });
        return params;
    }

    /**
     * 解析字符串值并转换为合适的对象类型
     * <p>
     * 根据字符串的格式自动识别并转换为相应的数据类型:<br/>
     * 如果是有效的 JSON 格式, 则解析为 JSON 对象或数组;<br/>
     * 如果是布尔值字符串 ("true" 或 "false"), 则转换为 Boolean 类型;<br/>
     * 如果是整数格式, 则转换为 Long 类型;<br/>
     * 如果是数字格式 (包括科学计数法), 则转换为 Double 类型;<br/>
     * 否则返回原始字符串.
     * @param value 待解析的字符串值
     * @return 解析后的对象, 可能是 JSON 对象,Boolean,Long,Double 或 String 类型
     */
    private static Object parseValue(final String value) {
        // 尝试识别JSON对象或数组
        if (JSON.isValid(value)) {
            return JSON.parse(value);
        }
        // 尝试识别布尔值
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Convert.toBool(value);
        }
        // 尝试识别整数
        if (NumberUtil.isLong(value)) {
            return Convert.toLong(value);
        }
        // 尝试识别浮点数
        if (NumberUtil.isDouble(value)) {
            return Convert.toDouble(value);
        }
        // 默认返回字符串
        return value;
    }

    /**
     * 将 Map 对象转换为 URL 参数字符串
     * <p>
     * 遍历 Map 中的键值对, 将每个键值对转换为 key=value 的形式,<br/>
     * 并使用 & 符号连接多个参数. 如果 Map 为空, 则返回空字符串.<br/>
     * 键和值都会被转换为字符串并进行 URL 编码.
     * @param map 待转换的 Map 对象, 键和值都可能为 null
     * @return 转换后的 URL 参数字符串, 格式为 key1=value1&key2=value2...
     */
    private static String convertMapToUrlParams(final Map<?, ?> map) {
        if (MapUtil.isEmpty(map)) return "";
        boolean first = Boolean.TRUE;
        final StringBuilder builder = new StringBuilder(map.size() * 24);
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            final Object key = entry.getKey();
            if (Objects.isNull(key)) continue;
            if (!first) {
                builder.append('&');
            } else {
                first = Boolean.FALSE;
            }
            // 直接编码，避免中间字符串创建
            encodeAndAppend(builder, Convert.toStr(key));
            builder.append('=');
            encodeAndAppend(builder, Convert.toStr(entry.getValue()));
        }
        return builder.toString();
    }

    /**
     * 将列表转换为 URL 参数字符串
     * <p>
     * 遍历列表中的每个元素, 将其转换为形如 "item0=value0&item1=value1" 的格式,<br/>
     * 其中 value 会进行 URL 编码. 如果列表为空或 null, 则返回空字符串.
     * @param list 待转换的列表, 元素可以为任意类型
     * @return 转换后的 URL 参数字符串, 格式为 "item0=value0&item1=value1..."
     */
    private static String convertListToUrlParams(final List<?> list) {
        if (CollUtil.isEmpty(list)) return "";
        boolean first = Boolean.TRUE;
        final StringBuilder builder = new StringBuilder(list.size() * 16);
        for (int i = 0, size = list.size(); i < size; i++) {
            final Object item = list.get(i);
            if (Objects.isNull(item)) continue;
            if (!first) {
                builder.append('&');
            } else {
                first = Boolean.FALSE;
            }
            builder.append("item").append(i).append('=');
            encodeAndAppend(builder, Convert.toStr(item));
        }
        return builder.toString();
    }

    /**
     * 对输入字符串进行编码并追加到构建器中
     * <p>
     * 该方法将输入字符串按照 UTF-8 编码转换为字节序列,<br/>
     * 然后对每个字节进行处理: 如果是保留字符则直接追加,<br/>
     * 否则将其转换为百分号编码格式 (%XX) 追加到构建器中
     * @param builder 用于追加编码结果的字符串构建器
     * @param input   待编码的输入字符串
     */
    private static void encodeAndAppend(final StringBuilder builder, final String input) {
        if (StrUtil.isEmpty(input)) return;
        final byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        for (final byte b : bytes) {
            final int c = b & 0xFF;
            if (isUnreserved(c)) {
                builder.append((char) c);
            } else {
                builder.append('%');
                builder.append(Character.toUpperCase(Character.forDigit((c >>> 4) & 0xF, 16)));
                builder.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
    }

    /**
     * 判断指定字符是否为非保留字符
     * <p>
     * 非保留字符包括: 小写字母 (a-z), 大写字母(A-Z), 数字(0-9) 以及特殊字符(-._~)
     * @param c 待判断的字符
     * @return 如果字符为非保留字符则返回 true, 否则返回 false
     */
    private static boolean isUnreserved(final int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~';
    }

    @Override
    public String convert(final String json) throws ConvertException {
        try {
            final Object parsed = JSON.parse(json);
            if (parsed instanceof final Map<?, ?> map) {
                return convertMapToUrlParams(map);
            } else if (parsed instanceof final List<?> list) {
                return convertListToUrlParams(list);
            } else {
                return json;
            }
        } catch (final Exception e) {
            return json;
        }
    }

    @Override
    public String reverseConvert(final String urlParams) {
        try {
            return new JsonFormatter().process(parseUrlParams(urlParams));
        } catch (final Exception e) {
            return urlParams;
        }
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.URL_PARAMS.equals(any);
    }
}