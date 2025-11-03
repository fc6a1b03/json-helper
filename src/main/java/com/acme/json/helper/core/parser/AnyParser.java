package com.acme.json.helper.core.parser;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;
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
 * 任意解析器<br/>
 * 目前支持：xml、yaml、toml、properties
 * @author xuhaifeng
 * @date 2025-05-05
 * @see AnyFile
 */
@SuppressWarnings({"RegExpRedundantClassElement", "UnnecessaryUnicodeEscape", "RegExpSimplifiable"})
public class AnyParser {
    /**
     * properties模式
     */
    private static final Pattern PROPERTIES_PATTERN = Pattern.compile(
            // 非空行、非注释、键=值（键和值均非空）
            "^(?!\\s*(?:#|$)).+?=.+$", Pattern.MULTILINE
    );

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