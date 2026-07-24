package com.acme.prism.core.archive;

import cn.hutool.core.util.StrUtil;
import com.acme.prism.core.fileinfo.FileCommentExtractor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 压缩包格式识别与条目读取
 * <p>
 * 基于 Apache Commons Compress，支持 zip 系（zip/jar/war/ear）、7z、tar 系（tar/tar.gz/tgz/tar.bz2/tbz2/tar.xz/txz）与单文件压缩（gz/bz2/xz）
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
public enum ArchiveFormats {
    /**
     * zip 系（含 jar/war/ear）
     */
    ZIP(Set.of("zip", "jar", "war", "ear"), ArchiveFormats::readZipEntries),
    /**
     * 7z
     */
    SEVEN_Z(Set.of("7z"), ArchiveFormats::readSevenZEntries),
    /**
     * tar 系（无中央目录，流式遍历）
     */
    TAR(Set.of("tar"), ArchiveFormats::readTarEntries),
    TAR_GZ(Set.of("tar.gz", "tgz"), ArchiveFormats::readTarEntries),
    TAR_BZ2(Set.of("tar.bz2", "tbz2"), ArchiveFormats::readTarEntries),
    TAR_XZ(Set.of("tar.xz", "txz"), ArchiveFormats::readTarEntries),
    /**
     * 单文件压缩（gz/bz2/xz，仅含一个条目）
     */
    GZIP(Set.of("gz"), ArchiveFormats::readSingleEntries),
    BZIP2(Set.of("bz2"), ArchiveFormats::readSingleEntries),
    XZ(Set.of("xz"), ArchiveFormats::readSingleEntries);

    /**
     * 支持的扩展名集合
     */
    private final Set<String> extensions;
    /**
     * 条目读取器（文件 → 原始条目列表）
     */
    private final EntryReader reader;

    /**
     * 条目读取函数（允许抛出 IOException）
     */
    @FunctionalInterface
    private interface EntryReader {
        /**
         * 读取压缩包全部原始条目
         *
         * @param file   压缩包文件
         * @param format 压缩包格式
         * @return 原始条目列表
         * @throws IOException 读取失败
         */
        List<RawEntry> read(File file, ArchiveFormats format) throws IOException;
    }

    ArchiveFormats(final Set<String> extensions, final EntryReader reader) {
        this.extensions = extensions;
        this.reader = reader;
    }

    /**
     * 文本条目注释提取条数上限（防超大包逐条解压头部拖慢索引构建）
     */
    private static final int COMMENT_EXTRACT_LIMIT = 5_000;

    /**
     * 原始条目
     *
     * @param path         包内完整路径
     * @param directory    是否目录
     * @param size         解压后大小（字节）
     * @param lastModified 条目最后修改时间（毫秒；无时间信息为 0）
     * @param comment      头部注释摘要（构建索引时顺带提取；无注释/非文本/超限为 null）
     */
    public record RawEntry(String path, boolean directory, long size, long lastModified, @Nullable String comment) {
    }

    /**
     * 提取条目头注释（UTF-8 解码头部字节，注释符为 ASCII 兼容，解码异常不影响识别）
     *
     * @param head 条目头部字节
     * @return 注释摘要；无注释返回 null
     */
    private static @Nullable String extractComment(final byte[] head) {
        return FileCommentExtractor.extract(new String(head, StandardCharsets.UTF_8));
    }

    /**
     * Date 转毫秒时间戳（null 与非法值归一为 0）
     *
     * @param date 条目时间
     * @return 毫秒时间戳
     */
    private static long millisOf(final Date date) {
        return Objects.nonNull(date) ? Math.max(date.getTime(), 0L) : 0L;
    }

    /**
     * 判断是否应提取条目头注释（非目录、非空、文本白名单、未超提取上限）
     *
     * @param directory 是否目录
     * @param size      条目解压后大小
     * @param name      条目名
     * @param extracted 已提取条数
     * @return boolean
     */
    private static boolean shouldExtractComment(final boolean directory, final long size, final String name, final int extracted) {
        return !directory && size > 0 && extracted < COMMENT_EXTRACT_LIMIT
                && ArchiveContentIndex.ArchiveSearchHelper.isTextEntry(name);
    }

    /**
     * 按文件名识别压缩包格式（先匹配复合扩展名如 tar.gz，再匹配单扩展名）
     *
     * @param fileName 文件名
     * @return 匹配的格式；不支持的返回 null
     */
    public static ArchiveFormats of(final String fileName) {
        if (StrUtil.isEmpty(fileName)) {
            return null;
        }
        final String lower = fileName.toLowerCase(Locale.ROOT);
        // 复合扩展名优先
        for (final ArchiveFormats format : values()) {
            for (final String extension : format.extensions) {
                if (extension.contains(".") && lower.endsWith("." + extension)) {
                    return format;
                }
            }
        }
        for (final ArchiveFormats format : values()) {
            for (final String extension : format.extensions) {
                if (!extension.contains(".") && lower.endsWith("." + extension)) {
                    return format;
                }
            }
        }
        return null;
    }

    /**
     * 判断是否为 tar 系格式（无中央目录，索引需全量流式遍历）
     *
     * @return boolean
     */
    public boolean isTarFamily() {
        return this == TAR || this == TAR_GZ || this == TAR_BZ2 || this == TAR_XZ;
    }

    /**
     * 拼接 zip 系条目在 JarFileSystem 中的 VFS 路径（系统无关路径 + 分隔符 + 条目路径）
     * <p>
     * 打开器、树节点、内容索引三处共用的路径规则，统一在此维护
     *
     * @param archiveFile 压缩包文件
     * @param entryPath   条目路径（空串表示压缩包根）
     * @return VFS 路径
     */
    public static String zipEntryVfsPath(final File archiveFile, final String entryPath) {
        return FileUtil.toSystemIndependentName(archiveFile.getAbsolutePath()) + JarFileSystem.JAR_SEPARATOR + entryPath;
    }

    /**
     * 单次流式遍历提取全部文本条目内容
     * <p>
     * 7z/tar 为顺序压缩格式，随机访问条目必须从头解压遍历（单条 O(N)）；
     * 统一采用单次遍历提取，全部条目共 O(N)，避免逐条读取的 O(N²) 灾难
     *
     * @param file           压缩包文件
     * @param isTextEntry    文本条目判断（按条目名）
     * @param maxEntrySize   单条目最大提取体积（字节）
     * @param maxEntries     最大提取条目数
     * @param contentHandler 条目内容处理器（路径、解压后大小、内容流）
     * @throws IOException 读取失败
     */
    public void forEachEntryContent(final File file,
                                    final java.util.function.Predicate<String> isTextEntry,
                                    final long maxEntrySize,
                                    final int maxEntries,
                                    final EntryContentHandler contentHandler) throws IOException {
        final int[] count = {0};
        switch (this) {
            case ZIP -> {
                try (final ZipFile zipFile = ZipFile.builder().setFile(file).get()) {
                    final var enumeration = zipFile.getEntries();
                    while (enumeration.hasMoreElements() && count[0] < maxEntries) {
                        final var entry = enumeration.nextElement();
                        if (entry.isDirectory() || entry.getSize() <= 0 || entry.getSize() > maxEntrySize || !isTextEntry.test(entry.getName())) {
                            continue;
                        }
                        try (final InputStream input = zipFile.getInputStream(entry)) {
                            contentHandler.accept(entry.getName(), input.readAllBytes());
                        }
                        count[0]++;
                    }
                }
            }
            case SEVEN_Z -> {
                try (final SevenZFile sevenZFile = SevenZFile.builder().setFile(file).get()) {
                    for (var entry = sevenZFile.getNextEntry(); Objects.nonNull(entry) && count[0] < maxEntries; entry = sevenZFile.getNextEntry()) {
                        if (entry.isDirectory() || entry.getSize() <= 0 || entry.getSize() > maxEntrySize || !isTextEntry.test(entry.getName())) {
                            continue;
                        }
                        contentHandler.accept(entry.getName(), readSevenZBytes(sevenZFile, (int) entry.getSize()));
                        count[0]++;
                    }
                }
            }
            default -> {
                // tar 系：单次流式遍历
                try (final TarArchiveInputStream stream = createStream(file, this)) {
                    for (ArchiveEntry entry = stream.getNextEntry(); Objects.nonNull(entry) && count[0] < maxEntries; entry = stream.getNextEntry()) {
                        if (entry.isDirectory() || entry.getSize() <= 0 || entry.getSize() > maxEntrySize || !isTextEntry.test(entry.getName())) {
                            continue;
                        }
                        contentHandler.accept(entry.getName(), stream.readAllBytes());
                        count[0]++;
                    }
                }
            }
        }
    }

    /**
     * 条目内容处理器（单次遍历时回调）
     */
    @FunctionalInterface
    public interface EntryContentHandler {
        /**
         * 处理单个文本条目的内容
         *
         * @param path    条目路径
         * @param content 条目内容
         */
        void accept(String path, byte[] content);
    }

    /**
     * 读取压缩包全部原始条目
     *
     * @param file 压缩包文件
     * @return 原始条目列表
     * @throws IOException 读取失败
     */
    public List<RawEntry> readEntries(final File file) throws IOException {
        return this.reader.read(file, this);
    }

    /**
     * 读取单个条目内容
     *
     * @param file      压缩包文件
     * @param entryPath 条目路径
     * @param maxSize   最大允许读取大小（字节），超出返回 null
     * @return 条目内容；条目不存在或超出大小限制返回 null
     * @throws IOException 读取失败
     */
    public byte[] readEntryContent(final File file, final String entryPath, final long maxSize) throws IOException {
        return switch (this) {
            case ZIP -> readZipEntryContent(file, entryPath, maxSize);
            case SEVEN_Z -> readSevenZEntryContent(file, entryPath, maxSize);
            default -> readStreamEntryContent(file, entryPath, maxSize);
        };
    }

    /* ---------------- zip 系 ---------------- */

    /**
     * 读取 ZIP 文件中的条目信息
     * <p> 打开指定的 ZIP 文件并读取其中的所有条目, 返回包含条目名称, 是否为目录以及大小信息的列表
     *
     * @param file    要读取的 ZIP 文件, 不能为 null
     * @param ignored 忽略的归档格式, 当前方法中未使用该参数
     * @return ZIP 文件中所有条目的列表, 每个条目包含名称, 是否为目录及大小信息
     * @throws IOException 当文件读取过程中发生 I/O 错误时抛出
     */
    private static List<RawEntry> readZipEntries(final File file, final ArchiveFormats ignored) throws IOException {
        try (final ZipFile zipFile = ZipFile.builder().setFile(file).get()) {
            final List<RawEntry> entries = new java.util.ArrayList<>();
            int extracted = 0;
            final var enumeration = zipFile.getEntries();
            while (enumeration.hasMoreElements()) {
                final var entry = enumeration.nextElement();
                String comment = null;
                // zip 为随机读结构：文本条目顺带解压头部提取注释（含提取条数护栏）
                if (shouldExtractComment(entry.isDirectory(), entry.getSize(), entry.getName(), extracted)) {
                    try (final InputStream input = zipFile.getInputStream(entry)) {
                        comment = extractComment(input.readNBytes(FileCommentExtractor.HEAD_BYTES));
                        extracted++;
                    }
                }
                entries.add(new RawEntry(entry.getName(), entry.isDirectory(), entry.getSize(),
                        Math.max(entry.getTime(), 0L), comment));
            }
            return entries;
        }
    }

    /**
     * 读取 ZIP 文件中指定条目的内容
     * <p> 从指定的 ZIP 文件中查找指定路径的条目, 并返回其内容字节数组
     * <p> 如果条目不存在, 是目录或大小超过最大限制, 则返回 null
     *
     * @param file      要读取的 ZIP 文件, 不能为 null
     * @param entryPath 要读取的条目路径, 不能为 null
     * @param maxSize   允许读取的最大字节大小, 不能为负数
     * @return 条目内容的字节数组, 如果条目不存在或不符合条件则返回 null
     * @throws IOException 当文件读取过程中发生错误时抛出
     */
    private static byte[] readZipEntryContent(final File file, final String entryPath, final long maxSize) throws IOException {
        try (final ZipFile zipFile = ZipFile.builder().setFile(file).get()) {
            final var entry = zipFile.getEntry(entryPath);
            if (Objects.isNull(entry) || entry.isDirectory() || entry.getSize() > maxSize) {
                return null;
            }
            try (final InputStream input = zipFile.getInputStream(entry)) {
                return input.readAllBytes();
            }
        }
    }

    /* ---------------- 7z ---------------- */

    /**
     * 读取 7-Zip 文件中的条目信息
     * <p> 从指定的 7-Zip 文件中读取所有条目, 并将其转换为原始条目对象列表
     *
     * @param file    要读取的 7-Zip 文件, 不能为 null
     * @param ignored 当前方法中未使用该参数
     * @return 包含所有条目信息的列表, 每个条目包含名称, 是否为目录以及大小信息
     * @throws IOException 当文件读取过程中发生错误时抛出
     */
    private static List<RawEntry> readSevenZEntries(final File file, final ArchiveFormats ignored) throws IOException {
        try (final SevenZFile sevenZFile = SevenZFile.builder().setFile(file).get()) {
            final List<RawEntry> entries = new java.util.ArrayList<>();
            int extracted = 0;
            for (var entry = sevenZFile.getNextEntry(); Objects.nonNull(entry); entry = sevenZFile.getNextEntry()) {
                String comment = null;
                // 顺序流当前位置即条目头：读头后由 getNextEntry 跳过剩余，单次遍历共 O(N)
                if (shouldExtractComment(entry.isDirectory(), entry.getSize(), entry.getName(), extracted)) {
                    comment = extractComment(readSevenZBytes(sevenZFile, FileCommentExtractor.HEAD_BYTES));
                    extracted++;
                }
                entries.add(new RawEntry(entry.getName(), entry.isDirectory(), entry.getSize(),
                        millisOf(entry.getLastModifiedDate()), comment));
            }
            return entries;
        }
    }

    /**
     * 读取 7z 当前条目字节（顺序流定位后最多读取 length 字节；实际不足时按实读长度截断返回）
     *
     * @param sevenZFile 7z 文件句柄
     * @param length     期望读取字节数
     * @return 实际读取到的字节
     * @throws IOException 读取失败
     */
    private static byte[] readSevenZBytes(final SevenZFile sevenZFile, final int length) throws IOException {
        final byte[] content = new byte[length];
        int offset = 0;
        for (int read = sevenZFile.read(content, offset, content.length - offset);
             read > 0 && offset < content.length;
             read = sevenZFile.read(content, offset, content.length - offset)) {
            offset += read;
        }
        return offset == content.length ? content : Arrays.copyOf(content, offset);
    }

    /**
     * 读取 7-Zip 文件中指定路径条目的内容
     * <p> 从指定的 7-Zip 文件中查找匹配路径的条目, 并读取其内容字节数组
     * <p> 如果未找到匹配条目或条目大小超过最大限制, 则返回 null
     *
     * @param file      7-Zip 文件对象, 不能为 null
     * @param entryPath 要查找的条目路径, 不能为 null
     * @param maxSize   允许的最大条目大小, 单位为字节
     * @return 条目内容的字节数组, 如果未找到匹配条目或超出大小限制则返回 null
     * @throws IOException 当文件读取过程中发生错误时抛出
     */
    private static byte[] readSevenZEntryContent(final File file, final String entryPath, final long maxSize) throws IOException {
        try (final SevenZFile sevenZFile = SevenZFile.builder().setFile(file).get()) {
            for (var entry = sevenZFile.getNextEntry(); Objects.nonNull(entry); entry = sevenZFile.getNextEntry()) {
                if (entryPath.equals(entry.getName()) && !entry.isDirectory() && entry.getSize() <= maxSize) {
                    return readSevenZBytes(sevenZFile, (int) entry.getSize());
                }
            }
            return null;
        }
    }

    /* ---------------- tar 系与单文件压缩（流式） ---------------- */

    /**
     * 创建归档输入流
     * <p> 根据指定的文件和归档格式创建对应的归档输入流, 用于读取归档文件内容
     *
     * @param file   要读取的归档文件, 不能为 null
     * @param format 归档文件格式, 不能为 null
     * @return 对应的归档输入流实例
     * @throws IOException 当文件读取或流操作失败时抛出
     */
    private static TarArchiveInputStream createStream(final File file, final ArchiveFormats format) throws IOException {
        return new TarArchiveInputStream(decompressStream(new BufferedInputStream(Files.newInputStream(file.toPath())), format));
    }

    /**
     * 按格式包装解压流（gzip/bzip2/xz；其他格式原样返回）
     *
     * @param input  原始输入流
     * @param format 压缩格式
     * @return 解压输入流
     */
    private static InputStream decompressStream(final InputStream input, final ArchiveFormats format) throws IOException {
        return switch (format) {
            case TAR_GZ, GZIP -> new GzipCompressorInputStream(input);
            case TAR_BZ2, BZIP2 -> new BZip2CompressorInputStream(input);
            case TAR_XZ, XZ -> new XZCompressorInputStream(input);
            default -> input;
        };
    }

    /**
     * 从归档文件中读取条目信息
     * <p> 根据指定的归档文件和格式读取所有条目, 并将每个条目的名称, 是否为目录以及大小信息封装成 RawEntry 对象
     *
     * @param file   归档文件对象, 不能为 null
     * @param format 归档文件格式, 不能为 null
     * @return 包含所有条目信息的列表, 如果无条目则返回空列表
     * @throws IOException 当读取归档文件过程中发生 I/O 错误时抛出
     */
    private static List<RawEntry> readTarEntries(final File file, final ArchiveFormats format) throws IOException {
        try (final TarArchiveInputStream stream = createStream(file, format)) {
            final List<RawEntry> entries = new java.util.ArrayList<>();
            int extracted = 0;
            for (ArchiveEntry entry = stream.getNextEntry(); Objects.nonNull(entry); entry = stream.getNextEntry()) {
                String comment = null;
                // 流式当前位置即条目头：读头后由 getNextEntry 跳过剩余，单次遍历共 O(N)
                if (shouldExtractComment(entry.isDirectory(), entry.getSize(), entry.getName(), extracted)) {
                    comment = extractComment(stream.readNBytes(FileCommentExtractor.HEAD_BYTES));
                    extracted++;
                }
                entries.add(new RawEntry(entry.getName(), entry.isDirectory(), entry.getSize(),
                        millisOf(entry.getLastModifiedDate()), comment));
            }
            return entries;
        }
    }

    /**
     * 读取单个文件条目信息
     * <p> 从指定文件中读取单个条目信息, 条目名称为去除压缩后缀的原文件名
     *
     * @param file   文件对象, 不能为 null
     * @param format 压缩格式, 不能为 null
     * @return 包含单个条目信息的列表, 包含条目名称, 是否为目录, 文件大小
     * @throws IOException 当文件读取或解压过程中发生错误时抛出
     */
    private static List<RawEntry> readSingleEntries(final File file, final ArchiveFormats format) throws IOException {
        // 单文件压缩：条目名取去掉压缩后缀的原文件名
        final String name = file.getName();
        final int dotIndex = name.lastIndexOf('.');
        final String entryName = dotIndex > 0 ? name.substring(0, dotIndex) : name;
        try (final InputStream input = decompressStream(new BufferedInputStream(Files.newInputStream(file.toPath())), format)) {
            final String comment = ArchiveContentIndex.ArchiveSearchHelper.isTextEntry(entryName)
                    ? extractComment(input.readNBytes(FileCommentExtractor.HEAD_BYTES))
                    : null;
            return List.of(new RawEntry(entryName, false, file.length(), file.lastModified(), comment));
        }
    }

    /**
     * 读取归档文件中的条目内容
     * <p> 根据指定的文件和条目路径, 从归档文件中读取对应的条目内容
     * <p> 该方法支持多种归档格式, 并对压缩文件进行大小限制保护, 防止解压炸弹攻击
     *
     * @param file      归档文件对象, 不能为 null
     * @param entryPath 要查找的条目路径, 不能为 null
     * @param maxSize   最大允许读取的条目大小 (单位: 字节), 超过此大小将返回 null
     * @return 条目内容的字节数组, 如果未找到对应条目或超出大小限制则返回 null
     * @throws IOException 当文件读取过程中发生错误时抛出
     */
    private static byte[] readStreamEntryContent(final File file, final String entryPath, final long maxSize) throws IOException {
        // tar 系与单文件压缩：流式遍历定位条目
        final ArchiveFormats format = ArchiveFormats.of(file.getName());
        if (Objects.isNull(format)) {
            return null;
        }
        if (format == GZIP || format == BZIP2 || format == XZ) {
            if (file.length() > maxSize * 4L) {
                // 压缩率兜底防护：压缩包过大时拒绝解压（防解压炸弹）
                return null;
            }
            try (final InputStream input = decompressStream(new BufferedInputStream(Files.newInputStream(file.toPath())), format)) {
                return input.readAllBytes();
            }
        }
        try (final TarArchiveInputStream stream = createStream(file, format)) {
            for (ArchiveEntry entry = stream.getNextEntry(); Objects.nonNull(entry); entry = stream.getNextEntry()) {
                if (entryPath.equals(entry.getName()) && !entry.isDirectory() && entry.getSize() <= maxSize) {
                    return stream.readAllBytes();
                }
            }
            return null;
        }
    }
}
