package com.acme.json.helper.core.search.item;

/**
 * HTTP请求项<<br/>
 * 表示一个HTTP请求文件
 * @param fileName 文件名
 * @param filePath 文件路径
 */
public record HttpRequestItem(String fileName, String filePath) {
    /**
     * 获取文件名称
     * <p>
     * 返回当前对象关联的文件名称
     * @return 文件名称
     */
    public String getFileName() {
        return this.fileName;
    }

    /**
     * 获取文件路径
     * <p>
     * 返回当前对象关联的文件路径字符串
     * @return 文件路径字符串
     */
    public String getFilePath() {
        return this.filePath;
    }
}