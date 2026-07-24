package com.acme.prism.core.search.item;

/**
 * HTTP请求项<br/>
 * 表示一个HTTP请求文件
 *
 * @param fileName 文件名
 * @param filePath 文件路径
 * @author 拒绝者
 * @date 2025-11-05
 */
public record HttpRequestItem(String fileName, String filePath) {
}
