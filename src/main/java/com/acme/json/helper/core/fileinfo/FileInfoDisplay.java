package com.acme.json.helper.core.fileinfo;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 文件信息展示格式化（纯函数）
 * <p>
 * 项目树文件名右侧辅助文本的统一拼接规则（本地文件与压缩包条目共用）：
 * 与文件名以双空格分隔，注释与修改时间以间隔点分隔，灰色展示由调用方指定
 *
 * @author 拒绝者
 * @date 2026-07-21
 */
public final class FileInfoDisplay {
    /**
     * 修改时间展示格式
     */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    /**
     * 注释与修改时间的分隔符
     */
    private static final String SEPARATOR = " · ";
    /**
     * 与文件名的分隔间距
     */
    private static final String PREFIX = "  ";

    private FileInfoDisplay() {
    }

    /**
     * 拼接文件名右侧展示文本
     *
     * @param comment    注释摘要（可为 null）
     * @param timeMillis 修改时间戳（毫秒；&lt;=0 表示无时间信息，如压缩包内合成目录）
     * @return 展示文本；注释与时间均缺失返回 null
     */
    public static @Nullable String format(final @Nullable String comment, final long timeMillis) {
        final String time = timeMillis > 0
                ? TIME_FORMAT.format(Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()))
                : null;
        if (comment == null) {
            return time == null ? null : PREFIX + time;
        }
        return time == null ? PREFIX + comment : PREFIX + comment + SEPARATOR + time;
    }
}
