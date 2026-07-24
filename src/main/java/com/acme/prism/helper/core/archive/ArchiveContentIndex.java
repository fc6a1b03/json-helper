package com.acme.prism.core.archive;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.*;

/**
 * 压缩包内容搜索索引（trigram 倒排）
 * <p>
 * 对每个压缩包的文本条目构建三元字符组倒排索引：
 * 查询时先以搜索词的三元组集合求交得到候选条目，再做 contains 精查，
 * 行号通过预计算的行偏移数组二分定位——查询成本与包内条目数基本无关。
 * 索引仅在首次内容搜索时构建一次，随压缩包变更整体失效重建。
 * <p>
 * zip 系压缩包的 .class 条目以 Fernflower 反编译文本参与索引（经平台 JarFileSystem 获取，
 * 与编辑器打开的反编译内容一致，行号可直接用于打开定位）；其他格式的 class 条目不参与内容索引
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
public final class ArchiveContentIndex {
    /**
     * 日志
     */
    private static final Logger LOG = Logger.getInstance(ArchiveContentIndex.class);
    /**
     * 参与内容索引的单条目最大体积（字节）
     */
    private static final long MAX_INDEXED_ENTRY_SIZE = 64L * 1024;
    /**
     * 单压缩包内容索引的最大条目数
     */
    private static final int MAX_INDEXED_ENTRIES = 3000;
    /**
     * 参与内容索引的 class 条目数上限（反编译有 CPU 成本，超限截断防止巨型 jar 拖慢首次搜索）
     */
    private static final int MAX_INDEXED_CLASS_ENTRIES = 2000;
    /**
     * 参与内容索引的单 class 原始字节上限（罕见巨型 class 直接跳过）
     */
    private static final long MAX_INDEXED_CLASS_ENTRY_SIZE = 512L * 1024;
    /**
     * 参与内容索引的单 class 反编译文本最大长度（字符数）
     */
    private static final int MAX_INDEXED_CLASS_TEXT_LENGTH = 128 * 1024;
    /**
     * class 条目扩展名
     */
    private static final String CLASS_EXTENSION = ".class";

    /**
     * 条目路径 → 文本内容（小写化，用于精查与行号定位）
     */
    private final Map<String, String> contents;
    /**
     * 条目路径 → 行起始偏移数组（升序，首个元素恒为 0）
     */
    private final Map<String, int[]> lineOffsetsByPath;
    /**
     * trigram（打包为 long）→ 包含该三元组的条目路径集合
     */
    private final Map<Long, Set<String>> trigramIndex;
    /**
     * 索引条目数
     */
    private final int indexedCount;

    private ArchiveContentIndex(final Map<String, String> contents,
                                final Map<String, int[]> lineOffsetsByPath,
                                final Map<Long, Set<String>> trigramIndex) {
        this.contents = contents;
        this.lineOffsetsByPath = lineOffsetsByPath;
        this.trigramIndex = trigramIndex;
        this.indexedCount = contents.size();
    }

    /**
     * 内容命中项
     *
     * @param path 条目路径
     * @param line 命中行号（1 起始）
     */
    public record ContentHit(String path, int line) {
    }

    /**
     * 构建压缩包内容索引（单次流式遍历提取，全包共 O(N)，避免逐条随机读取的 O(N²)）
     * <p>
     * 文本条目提取完成后，zip 系压缩包追加 class 反编译文本索引阶段；
     * 构建过程不可被进度取消打断（ProcessCanceledException 一律重抛，由调用方决定是否中止）
     *
     * @param archiveFile 压缩包文件
     * @param format      压缩包格式
     * @param index       条目索引（提供条目清单）
     * @return 内容索引
     */
    public static ArchiveContentIndex build(final File archiveFile, final ArchiveFormats format, final ArchiveIndex index) {
        final Map<String, String> contents = new HashMap<>();
        final Map<String, int[]> lineOffsetsByPath = new HashMap<>();
        final Map<Long, Set<String>> trigramIndex = new HashMap<>();
        // 树中已知文件条目（用于过滤非索引内条目）
        final Set<String> knownPaths = new HashSet<>();
        for (final ArchiveIndex.Node node : index.allNodes()) {
            if (!node.directory()) {
                knownPaths.add(node.path());
            }
        }
        try {
            format.forEachEntryContent(archiveFile,
                    ArchiveSearchHelper::isTextEntry,
                    MAX_INDEXED_ENTRY_SIZE,
                    MAX_INDEXED_ENTRIES,
                    (path, content) -> {
                        final String normalized = path.replace('\\', '/');
                        if (!knownPaths.contains(normalized)) {
                            return;
                        }
                        registerEntry(normalized, new String(content, java.nio.charset.StandardCharsets.UTF_8)
                                .toLowerCase(java.util.Locale.ROOT), contents, lineOffsetsByPath, trigramIndex);
                    });
        } catch (final ProcessCanceledException pce) {
            // 控制流异常必须重抛，不得吞没或记录
            throw pce;
        } catch (final Exception e) {
            // 提取中断时保留已索引部分（部分损坏的包不应整体失效）
        }
        if (format == ArchiveFormats.ZIP) {
            // zip 系追加 class 反编译文本索引（jar 是 class 的主要载体；其他格式跳过）
            indexClassEntries(archiveFile, index, knownPaths, contents, lineOffsetsByPath, trigramIndex);
        }
        return new ArchiveContentIndex(contents, lineOffsetsByPath, trigramIndex);
    }

    /**
     * 索引 zip 系压缩包内的 class 条目（以平台反编译文本为索引内容）
     * <p>
     * 反编译文本经 JarFileSystem + LoadTextUtil 获取，与编辑器打开 class 时展示的内容一致，
     * 命中行号可直接用于打开定位；平台反编译缓存复用，已反编译过的条目近零成本
     *
     * @param archiveFile       压缩包文件
     * @param index             条目索引
     * @param knownPaths        树中已知文件条目路径
     * @param contents          条目路径 → 小写文本
     * @param lineOffsetsByPath 条目路径 → 行偏移数组
     * @param trigramIndex      trigram 倒排
     */
    private static void indexClassEntries(final File archiveFile, final ArchiveIndex index, final Set<String> knownPaths,
                                          final Map<String, String> contents, final Map<String, int[]> lineOffsetsByPath,
                                          final Map<Long, Set<String>> trigramIndex) {
        try {
            // 一次性 refresh 定位 jar 根（主动建立 VFS 句柄，不依赖条目是否被打开过），后续条目查找纯内存
            final VirtualFile jarRoot = JarFileSystem.getInstance().refreshAndFindFileByPath(
                    ArchiveFormats.zipEntryVfsPath(archiveFile, ""));
            if (Objects.isNull(jarRoot)) {
                LOG.warn("class 内容索引跳过，无法定位 jar 根: " + archiveFile);
                return;
            }
            int classCount = 0;
            for (final ArchiveIndex.Node node : index.allNodes()) {
                if (classCount >= MAX_INDEXED_CLASS_ENTRIES) {
                    return;
                }
                if (node.directory() || !node.name().endsWith(CLASS_EXTENSION)
                        || node.size() > MAX_INDEXED_CLASS_ENTRY_SIZE || !knownPaths.contains(node.path())) {
                    continue;
                }
                final VirtualFile classFile = jarRoot.findFileByRelativePath(node.path());
                if (Objects.isNull(classFile)) {
                    continue;
                }
                try {
                    final String text = LoadTextUtil.loadText(classFile).toString();
                    if (text.isEmpty() || text.length() > MAX_INDEXED_CLASS_TEXT_LENGTH) {
                        continue;
                    }
                    registerEntry(node.path(), text.toLowerCase(java.util.Locale.ROOT), contents, lineOffsetsByPath, trigramIndex);
                    classCount++;
                } catch (final ProcessCanceledException pce) {
                    throw pce;
                } catch (final Exception e) {
                    // 单个 class 反编译失败不影响整体索引
                    LOG.debug("class 反编译索引失败: " + node.path(), e);
                }
            }
        } catch (final ProcessCanceledException pce) {
            throw pce;
        } catch (final Exception e) {
            // class 索引阶段失败时保留已索引的文本部分
            LOG.warn("class 内容索引阶段失败: " + archiveFile, e);
        }
    }

    /**
     * 注册条目内容到倒排索引（文本与 class 反编译文本共用）
     *
     * @param path              条目路径
     * @param lowerText         小写化文本
     * @param contents          条目路径 → 小写文本
     * @param lineOffsetsByPath 条目路径 → 行偏移数组
     * @param trigramIndex      trigram 倒排
     */
    private static void registerEntry(final String path, final String lowerText,
                                      final Map<String, String> contents, final Map<String, int[]> lineOffsetsByPath,
                                      final Map<Long, Set<String>> trigramIndex) {
        contents.put(path, lowerText);
        lineOffsetsByPath.put(path, computeLineOffsets(lowerText));
        for (final long trigram : trigramsOf(lowerText)) {
            trigramIndex.computeIfAbsent(trigram, _ -> new HashSet<>()).add(path);
        }
    }

    /**
     * 按搜索词查询内容命中（trigram 交集 + contains 精查 + 行号二分定位）
     *
     * @param lowerPattern 小写搜索词（长度需 ≥ 3，否则无 trigram 可用）
     * @return 命中项列表
     */
    public List<ContentHit> query(final String lowerPattern) {
        final Set<Long> patternTrigrams = trigramsOf(lowerPattern);
        if (patternTrigrams.isEmpty()) {
            return List.of();
        }
        // 以最少命中的 trigram 起步，逐步求交（交集越小迭代越少）
        Set<String> candidates = null;
        final List<Long> sortedTrigrams = patternTrigrams.stream()
                .sorted(java.util.Comparator.comparingInt(t -> this.trigramIndex.getOrDefault(t, Set.of()).size()))
                .toList();
        for (final long trigram : sortedTrigrams) {
            final Set<String> holders = this.trigramIndex.get(trigram);
            if (Objects.isNull(holders)) {
                return List.of();
            }
            if (Objects.isNull(candidates)) {
                candidates = new HashSet<>(holders);
            } else {
                candidates.retainAll(holders);
            }
            if (candidates.isEmpty()) {
                return List.of();
            }
        }
        // 精查与行号定位
        final List<ContentHit> hits = new ArrayList<>();
        for (final String path : Objects.requireNonNull(candidates)) {
            final String lowerText = this.contents.get(path);
            final int hitIndex = Objects.nonNull(lowerText) ? lowerText.indexOf(lowerPattern) : -1;
            if (hitIndex >= 0) {
                final int[] lineOffsets = this.lineOffsetsByPath.get(path);
                final int line = Arrays.binarySearch(lineOffsets, hitIndex);
                // binarySearch 未命中时返回 (-insertionPoint - 1)，换算为行号（1 起始）
                hits.add(new ContentHit(path, line >= 0 ? line + 1 : -line - 1));
            }
        }
        return hits;
    }

    /**
     * 索引条目数
     *
     * @return int
     */
    public int indexedCount() {
        return this.indexedCount;
    }

    /**
     * 计算文本的行起始偏移数组
     *
     * @param text 原始文本
     * @return 升序偏移数组（首个元素恒为 0）
     */
    private static int[] computeLineOffsets(final String text) {
        final List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && i + 1 < text.length()) {
                offsets.add(i + 1);
            }
        }
        return offsets.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * 提取文本的 trigram 集合（打包为 long：c1&lt;&lt;32 | c2&lt;&lt;16 | c3）
     *
     * @param lowerText 小写文本
     * @return trigram 集合
     */
    private static Set<Long> trigramsOf(final String lowerText) {
        final Set<Long> trigrams = new HashSet<>();
        for (int i = 0; i + 2 < lowerText.length(); i++) {
            trigrams.add(((long) lowerText.charAt(i) << 32) | ((long) lowerText.charAt(i + 1) << 16) | lowerText.charAt(i + 2));
        }
        return trigrams;
    }

    /**
     * 文本条目判断辅助（委托 ArchiveSearch 的扩展名白名单）
     */
    static final class ArchiveSearchHelper {
        private ArchiveSearchHelper() {
        }

        /**
         * 判断条目名是否为可参与内容索引的文本类文件
         *
         * @param name 条目名
         * @return boolean
         */
        static boolean isTextEntry(final String name) {
            final int dotIndex = name.lastIndexOf('.');
            if (dotIndex <= 0) {
                // 无扩展名（README/LICENSE 等）按文本处理
                return Boolean.TRUE;
            }
            return ArchiveSearch.TEXT_ENTRY_EXTENSIONS.contains(name.substring(dotIndex + 1).toLowerCase(java.util.Locale.ROOT));
        }
    }
}
