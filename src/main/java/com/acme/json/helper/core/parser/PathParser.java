package com.acme.json.helper.core.parser;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;

import java.net.URI;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 路径解析器<br/>
 * Path转JSON
 *
 * @author 拒绝者
 * @date 2025-01-27
 */
public class PathParser {
    private static final Pattern WEB_PATH_PATTERN = Pattern.compile("^(https?|ftp)://[^\\s/$.?#].\\S*$");
    /**
     * file:// 协议前缀
     */
    private static final String FILE_PROTOCOL_PREFIX = "file://";
    /**
     * HTTP 请求超时（毫秒），防止慢响应挂起后台线程
     */
    private static final int HTTP_TIMEOUT_MS = 5000;

    /**
     * 将Path转换为JSON
     *
     * @param text 文本
     * @return {@link String }
     */
    public static String convert(final String text) {
        if (StrUtil.isNotEmpty(text)) {
            // 匹配`Web`路径
            if (isWebPath(text)) {
                return fetchWebContent(text);
            }
            // 匹配`local`路径
            if (isLocalPath(text)) {
                return readLocalFile(text);
            }
        }
        return "";
    }

    /**
     * 是`Web`路径
     *
     * @param text 文本
     * @return boolean
     */
    private static boolean isWebPath(final String text) {
        return WEB_PATH_PATTERN.matcher(text).matches();
    }

    /**
     * 是`local`路径
     *
     * @param path 路径
     * @return boolean
     */
    private static boolean isLocalPath(final String path) {
        try {
            return Opt.of(path.startsWith(FILE_PROTOCOL_PREFIX)).filter(i -> i).orElseGet(() -> Paths.get(path).isAbsolute());
        } catch (final Exception ignored) {
            return Boolean.FALSE;
        }
    }

    /**
     * 获取网络内容（同步 HTTP GET，由调用方保证在后台线程执行）
     * <br/>
     * 特性：
     * - 自动关闭HTTP响应连接
     * - 仅返回200状态码的成功响应内容
     * - 支持HTTPS协议
     * - 异常时返回null（包含网络错误、超时、解析失败等情况）
     *
     * @param url 完整的HTTP/HTTPS地址，需要包含协议头（如http://或https://）
     * @return CompletableFuture<String> 异步结果容器，成功时包含网页内容字符串，失败返回null
     */
    private static String fetchWebContent(final String url) {
        try (final HttpResponse response = HttpUtil.createGet(url).timeout(HTTP_TIMEOUT_MS).execute()) {
            // 使用响应状态码过滤，仅`>= 200 && < 300`状态码视为成功
            return Opt.of(response.isOk()).filter(i -> i).map(_ -> response.body()).orElse("");
        } catch (final Exception ignored) {
            return "";
        }
    }

    /**
     * 读取本地文件内容（同步 IO，由调用方保证在后台线程执行）
     * <br/>
     * 特性：
     * - 自动处理文件编码（UTF-8）
     * - 支持两种路径格式：
     * 1. file://协议路径（如file:///path/to/file）
     * 2. 直接文件系统路径（如/path/to/file 或 C:\path\to\file）
     * - 异常时返回null（包含文件不存在、权限问题、编码错误等）
     *
     * @param path 文件路径（支持标准URI格式或直接路径）
     * @return CompletableFuture<String> 异步结果容器，成功时包含文件内容字符串，失败返回null
     */
    private static String readLocalFile(final String path) {
        try {
            // 双重路径处理逻辑：优先识别`file://`协议格式
            return Opt.of(path.startsWith(FILE_PROTOCOL_PREFIX)).filter(i -> i)
                    .map(_ -> FileUtil.readUtf8String(Paths.get(URI.create(path)).toFile()))
                    .orElseGet(() -> FileUtil.readUtf8String(path));
        } catch (final Exception ignored) {
            return "";
        }
    }
}
