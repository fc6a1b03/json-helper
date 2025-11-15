package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.json.JsonFormatter;
import com.felipestanzani.jtoon.*;

/**
 * TOON转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class ToonConverter implements DataFormatConverter {
    /**
     * 编码选项配置常量
     * <p>
     * 默认配置: 编码级别为 2, 分隔符为制表符, 数组呈现为[N]
     * @see EncodeOptions
     * @see Delimiter
     */
    private final static EncodeOptions ENCODE_OPTIONS = new EncodeOptions(2, Delimiter.TAB, Boolean.FALSE);
    /**
     * 解码选项常量, 用于配置解码过程中的各项参数
     * <p>
     * 包含最大分割数, 分隔符, 严格模式, 路径扩展模式
     * @see DecodeOptions
     * @see Delimiter
     * @see PathExpansion
     */
    private final static DecodeOptions DECODE_OPTIONS = new DecodeOptions(2, Delimiter.TAB, Boolean.TRUE, PathExpansion.SAFE);

    @Override
    public String convert(final String json) throws ConvertException {
        // 2025-06-02 注意：JToon v0.1.3 对“纯字符串数组”编码会丢真值
        // 例：{"tags":["-"]} → tags[1]{0}:\n  -  → 解码后永远带 "-" 文本
        // TODO 等官方修复，或先用 washForToon() 洗成对象数组再编码
        return new JsonFormatter().process(JToon.encodeJson(json, ENCODE_OPTIONS));
    }

    @Override
    public String reverseConvert(final String any) {
        // 2025-06-02 注意：若输入含 "- " 行，解码后字符串会残留 "-" 字面量
        // 根源：JToon 把 "- " 当普通文本，不识别 YAML 列表语义
        return new JsonFormatter().process(JToon.decode(any, DECODE_OPTIONS));
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.TOON.equals(any);
    }
}