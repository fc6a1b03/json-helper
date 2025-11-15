package com.acme.json.helper.core.parser;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
import com.felipestanzani.jtoon.DecodeOptions;
import com.felipestanzani.jtoon.JToon;
import org.gradle.internal.impldep.org.tomlj.Toml;
import org.xml.sax.InputSource;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * 通用解析器类
 * <p>
 * 提供对多种数据格式 (如 JSON,XML,TOON,YAML,TOML,Properties 等) 的自动识别与转换功能.<br/>
 * 该类通过检测输入字符串的内容类型, 决定其格式并进行相应的反向转换处理.
 * @author 拒绝者
 * @date 2025-05-05
 * @see AnyFile
 */
@SuppressWarnings({"RegExpRedundantClassElement", "UnnecessaryUnicodeEscape", "RegExpSimplifiable"})
public class AnyParser {
    /**
     * 属性配置文件的正则表达式模式
     * <p>
     * 用于匹配属性文件中的键值对行, 排除以 # 或空白字符开头的注释行和空行<br/>
     * 格式为:key=value, 其中 key 和 value 可以包含任意字符 (除换行符)
     * @see java.util.regex.Pattern
     */
    private static final Pattern PROPERTIES_PATTERN = Pattern.compile("^(?!\\s*(?:#|$)).+?=.+$", Pattern.MULTILINE);

    /**
     * 任意转换
     * @param any 任何
     * @return 异步JSON结果
     */
    public static CompletableFuture<String> convert(final String any) {
        return CompletableFuture.supplyAsync(() ->
                JSON.isValid(any) ? "" : Opt.ofBlankAble(any)
                        .map(item -> JsonParser.reverseConvert(item, detectType(any)))
                        .filter(StrUtil::isNotEmpty)
                        .orElse("")
        );
    }

    /**
     * 检测类型
     * @param input 输入
     * @return {@link AnyFile }
     */
    private static AnyFile detectType(final String input) {
        if (isXml(input)) return AnyFile.XML;
        if (isToon(input)) return AnyFile.TOON;
        if (isYaml(input)) return AnyFile.YAML;
        if (isToml(input)) return AnyFile.TOML;
        if (isProperties(input)) return AnyFile.PROPERTIES;
        return null;
    }

    /**
     * 是xml
     * @param input 输入
     * @return boolean
     */
    private static boolean isXml(final String input) {
        final String trimmed = input.trim();
        if (!trimmed.startsWith("<")) return Boolean.FALSE;
        try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(trimmed)));
            return Boolean.TRUE;
        } catch (final Exception e) {
            return Boolean.FALSE;
        }
    }

    /**
     * 检测字符串是否为 TOON 格式
     * @param input 输入字符串
     * @return boolean 是否为 TOON 格式
     */
    private static boolean isToon(final String input) {
        try {
            JToon.decode(input, DecodeOptions.DEFAULT);
            return Boolean.TRUE;
        } catch (final Exception e) {
            return Boolean.FALSE;
        }
    }

    /**
     * 是yaml
     * @param input 输入
     * @return boolean
     */
    private static boolean isYaml(final String input) {
        try {
            final Object result = new Yaml().load(input);
            return result instanceof Map || result instanceof List<?>;
        } catch (final Exception e) {
            return Boolean.FALSE;
        }
    }

    /**
     * 是toml
     * @param input 输入
     * @return boolean
     */
    private static boolean isToml(final String input) {
        try {
            return Toml.parse(input).errors().isEmpty();
        } catch (final Exception e) {
            return Boolean.FALSE;
        }
    }

    /**
     * 是properties
     * @param input 输入
     * @return boolean
     */
    private static boolean isProperties(final String input) {
        if (!PROPERTIES_PATTERN.matcher(input).find()) {
            return Boolean.FALSE;
        }
        try {
            new Properties().load(new StringReader(input));
            return Boolean.TRUE;
        } catch (final Exception e) {
            return Boolean.FALSE;
        }
    }
}