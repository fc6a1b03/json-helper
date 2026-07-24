package com.acme.prism.core.fileinfo;

import cn.hutool.core.util.StrUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 文件头注释提取器（纯函数）
 * <p>
 * 从文件头部文本中提取首段注释，作为项目树文件名右侧的辅助说明。
 * 支持主流注释风格（//、#、/*、&#60;!--、--、;; 与 Python docstring 三引号）；
 * package/import/using 声明与 shebang 自动跳过；遇到首行有效代码即放弃，
 * 保证项目树渲染零误判；结果按码点截断，避免长注释挤压修改时间的展示空间
 *
 * @author 拒绝者
 * @date 2026-07-21
 */
public final class FileCommentExtractor {
    /**
     * 头部注释读取字节数上限（注释必在文件开头，读取全文无意义；本地文件与压缩包条目共用）
     */
    public static final int HEAD_BYTES = 8192;
    /**
     * 注释摘要最大码点数（超出截断并补省略号；约 10 个汉字，连同修改时间整体保持单行紧凑展示）
     */
    private static final int MAX_CODE_POINTS = 20;
    /**
     * 括号补充段回退截断的最短主段码点数（主段过短说明括号是正文不可分割部分，不回退）
     */
    private static final int MIN_MAIN_CODE_POINTS = 8;
    /**
     * 扫描行数上限（防止畸形文件头失控；仅计有效判定行，package/import 等声明行不计）
     */
    private static final int SCAN_LINE_LIMIT = 12;
    /**
     * 截断省略号
     */
    private static final String ELLIPSIS = "…";
    /**
     * 块注释起始符 → 终结符（含 Python 模块 docstring 三引号）
     */
    private static final Map<String, String> BLOCK_COMMENT_END = Map.of(
            "/*", "*/",
            "<!--", "-->",
            "\"\"\"", "\"\"\"",
            "'''", "'''");
    /**
     * 单行注释前缀（# 需额外排除预处理指令，单独处理）
     */
    private static final List<String> LINE_COMMENT_PREFIXES = List.of("//", "--", ";;");
    /**
     * C 系预处理指令行（#include/#define 等属于代码而非注释，命中即终止扫描）
     */
    private static final Pattern PREPROCESSOR = Pattern.compile(
            "^#\\s*(include|define|if|ifdef|ifndef|elif|else|endif|undef|pragma|error|warning|line|import)\\b");
    /**
     * 可跳过的文件头声明行（shebang、XML 声明、DOCTYPE）
     */
    private static final Pattern SKIPPABLE_HEADER = Pattern.compile("^(#!|<\\?xml|<!DOCTYPE)", Pattern.CASE_INSENSITIVE);
    /**
     * 可跳过的包/导入声明行（Java/Kotlin 等的 package、import，C# 的 using；类文档注释常位于其之后）
     */
    private static final Pattern SKIPPABLE_STATEMENT = Pattern.compile("^(package|import|using)\\s");
    /**
     * 注释文本前导装饰字符（分割线、星号框等）
     */
    private static final Pattern LEADING_DECORATION = Pattern.compile("^[\\s*=\\-#~_]+");
    /**
     * 注释文本尾随装饰字符（含 Markdown 闭合式标题的 #）
     */
    private static final Pattern TRAILING_DECORATION = Pattern.compile("[\\s*=\\-#~_]+$");
    /**
     * 注释内部连续空白
     */
    private static final Pattern INNER_WHITESPACE = Pattern.compile("\\s+");
    /**
     * 尾随括号补充段（全角/半角，可连续多个；树列表仅保留主名称描述）
     */
    private static final Pattern TRAILING_PAREN = Pattern.compile("(\\s*(?:（[^（）]*）|\\([^()]*\\)))+$");
    /**
     * 分句标点（取首句语言描述；句点 . 不在列，防误伤 v1.0 等版本号文本）
     */
    private static final Pattern CLAUSE_BREAK = Pattern.compile("[，。；：,;]");
    /**
     * 常见 HTML 标签（白名单剥离为纯文本，如 &lt;b&gt;/&lt;br&gt;；泛型等技术文本不在名单内不受影响）
     */
    private static final Pattern HTML_TAG = Pattern.compile(
            "</?(?:b|i|u|s|em|strong|small|sub|sup|br|hr|p|a|span|div|code|pre|kbd|var|font|h[1-6]|li|ul|ol|dl|dt|dd|table|thead|tbody|tr|td|th|img|link|meta|html|head|body|title)(?:\\s[^>]*)?/?>",
            Pattern.CASE_INSENSITIVE);

    private FileCommentExtractor() {
    }

    /**
     * 提取文件头注释摘要
     *
     * @param head 文件头部文本
     * @return 注释摘要（已清理装饰并按码点截断）；无头部注释返回 null
     */
    public static @Nullable String extract(final String head) {
        if (StrUtil.isEmpty(head)) {
            return null;
        }
        // 非 null 表示处于块注释内，值为该块的终结符
        String terminator = null;
        int scanned = 0;
        for (final String raw : head.split("\n")) {
            final String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (terminator == null && (SKIPPABLE_HEADER.matcher(line).find() || SKIPPABLE_STATEMENT.matcher(line).find())) {
                // 文件头声明与包/导入声明：跳过且不计入扫描行数
                continue;
            }
            if (scanned >= SCAN_LINE_LIMIT) {
                return null;
            }
            scanned++;
            if (terminator != null) {
                // 块注释内：取首行非空文本；遇终结符则本块定案
                final String text = cleanBlockLine(line, terminator);
                if (line.contains(terminator) || text != null) {
                    return text;
                }
                continue;
            }
            if (line.startsWith("#")) {
                if (PREPROCESSOR.matcher(line).find()) {
                    return null;
                }
                final String text = clean(line.substring(1));
                if (text != null) {
                    return text;
                }
                continue;
            }
            final String blockStart = blockStartOf(line);
            if (blockStart != null) {
                final String blockEnd = BLOCK_COMMENT_END.get(blockStart);
                final String rest = line.substring(blockStart.length());
                final int close = rest.indexOf(blockEnd);
                if (close >= 0) {
                    // 单行块注释：本行定案
                    return clean(rest.substring(0, close));
                }
                terminator = blockEnd;
                final String text = clean(rest);
                if (text != null) {
                    return text;
                }
                continue;
            }
            final String linePrefix = linePrefixOf(line);
            if (linePrefix != null) {
                final String text = clean(line.substring(linePrefix.length()));
                if (text != null) {
                    return text;
                }
                continue;
            }
            // 首行有效代码：文件无头部注释
            return null;
        }
        return null;
    }

    /**
     * 匹配块注释起始符
     *
     * @param line 已 strip 的行
     * @return 起始符；未匹配返回 null
     */
    private static @Nullable String blockStartOf(final String line) {
        for (final String start : BLOCK_COMMENT_END.keySet()) {
            if (line.startsWith(start)) {
                return start;
            }
        }
        return null;
    }

    /**
     * 匹配单行注释前缀
     *
     * @param line 已 strip 的行
     * @return 前缀；未匹配返回 null
     */
    private static @Nullable String linePrefixOf(final String line) {
        for (final String prefix : LINE_COMMENT_PREFIXES) {
            if (line.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    /**
     * 清理块注释内行文本（剥终结符与延续行星号前缀）
     *
     * @param line       已 strip 的行
     * @param terminator 块终结符
     * @return 注释文本；无有效内容返回 null
     */
    private static @Nullable String cleanBlockLine(final String line, final String terminator) {
        String text = line;
        final int close = text.indexOf(terminator);
        if (close >= 0) {
            text = text.substring(0, close);
        }
        if (text.startsWith("*")) {
            text = text.substring(1);
        }
        return clean(text);
    }

    /**
     * 清理注释文本（树列表空间寸土寸金，仅保留语言描述：剥 HTML 标签、Javadoc 标签行、
     * 尾随括号补充段、首句之后内容，再去边缘装饰、压缩空白、按码点截断）
     *
     * @param raw 原始注释文本
     * @return 清理后的摘要；无有效内容返回 null
     */
    private static @Nullable String clean(final String raw) {
        // 先剥离 HTML 标签为空格（防粘连）
        String text = HTML_TAG.matcher(raw).replaceAll(" ").strip();
        if (text.startsWith("@")) {
            // Javadoc 标签行（@author/@date 等）不是语言描述
            return null;
        }
        // 剥尾随括号补充段、取首句描述
        text = TRAILING_PAREN.matcher(text).replaceFirst("");
        final var clause = CLAUSE_BREAK.matcher(text);
        if (clause.find()) {
            text = text.substring(0, clause.start());
        }
        text = TRAILING_DECORATION.matcher(LEADING_DECORATION.matcher(text).replaceFirst("")).replaceFirst("");
        text = INNER_WHITESPACE.matcher(text).replaceAll(" ").strip();
        if (text.isEmpty()) {
            return null;
        }
        return truncate(text);
    }

    /**
     * 按码点截断（避免切断代理对；窗口内存在括号补充段且主名足够完整时，
     * 整体丢弃括号段而非切出半括号，保持树列表文本干净）
     *
     * @param text 注释文本
     * @return 截断后的摘要
     */
    private static @NotNull String truncate(final String text) {
        if (text.codePointCount(0, text.length()) <= MAX_CODE_POINTS) {
            return text;
        }
        final int paren = parenStartOf(text);
        if (paren > 0) {
            final int mainCodePoints = text.codePointCount(0, paren);
            if (mainCodePoints >= MIN_MAIN_CODE_POINTS && mainCodePoints <= MAX_CODE_POINTS) {
                return text.substring(0, paren).strip();
            }
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_CODE_POINTS)) + ELLIPSIS;
    }

    /**
     * 首个括号（全角/半角）起始位置
     *
     * @param text 注释文本
     * @return 括号 char 索引；无括号返回 -1
     */
    private static int parenStartOf(final String text) {
        final int fullWidth = text.indexOf('（');
        final int halfWidth = text.indexOf('(');
        if (fullWidth < 0) {
            return halfWidth;
        }
        return halfWidth < 0 ? fullWidth : Math.min(fullWidth, halfWidth);
    }
}
