package com.acme.json.helper.core.archive;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 压缩包内容搜索索引（trigram 倒排）
 * <p>
 * 对每个压缩包的文本条目构建三元字符组倒排索引：
 * 查询时先以搜索词的三元组集合求交得到候选条目，再做 contains 精查，
 * 行号通过预计算的行偏移数组二分定位——查询成本与包内条目数基本无关。
 * 索引仅在首次内容搜索时构建一次，随压缩包变更整体失效重建。
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
public final class ArchiveContentIndex {
    /**
     * 参与内容索引的单条目最大体积（字节）
     */
    private static final long MAX_INDEXED_ENTRY_SIZE = 64L * 1024;
    /**
     * 单压缩包内容索引的最大条目数
     */
    private static final int MAX_INDEXED_ENTRIES = 3000;

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
                        final String lowerText = new String(content, java.nio.charset.StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
                        contents.put(normalized, lowerText);
                        lineOffsetsByPath.put(normalized, computeLineOffsets(lowerText));
                        for (final long trigram : trigramsOf(lowerText)) {
                            trigramIndex.computeIfAbsent(trigram, _ -> new HashSet<>()).add(normalized);
                        }
                    });
        } catch (final Exception e) {
            // 提取中断时保留已索引部分（部分损坏的包不应整体失效）
        }
        return new ArchiveContentIndex(contents, lineOffsetsByPath, trigramIndex);
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
