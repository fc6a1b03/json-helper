package com.acme.prism.core.archive;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 压缩包条目索引（目录树结构）
 * <p>
 * 将压缩包内全部条目按路径段组织为树，支持按父路径懒取子层，构建后全程内存访问无 IO
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
public final class ArchiveIndex {
    /**
     * 单压缩包最大条目数（超出拒绝索引，防超大包拖垮内存）
     */
    public static final int MAX_ENTRIES = 100_000;
    /**
     * 可索引的压缩包最大体积（zip/7z 有中央目录读取快，tar 系需全量流式遍历成本高）
     */
    public static final long MAX_ARCHIVE_SIZE_STREAM = 200L * 1024 * 1024;
    /**
     * tar 系可索引的最大体积（无中央目录，遍历成本与体积成正比）
     */
    public static final long MAX_ARCHIVE_SIZE_TAR = 50L * 1024 * 1024;
    /**
     * 单条目可打开的最大体积（字节）
     */
    public static final long MAX_ENTRY_OPEN_SIZE = 8L * 1024 * 1024;
    /**
     * 根层父路径标记
     */
    public static final String ROOT_PARENT = "";
    /**
     * 父路径 → 子条目列表（目录在前、按名称排序）
     */
    private final Map<String, List<Node>> childrenByParent;
    /**
     * 条目总数
     */
    private final int entryCount;

    private ArchiveIndex(final Map<String, List<Node>> childrenByParent, final int entryCount) {
        this.childrenByParent = childrenByParent;
        this.entryCount = entryCount;
    }

    /**
     * 条目节点
     *
     * @param path         包内完整路径（目录以 / 结尾归一）
     * @param name         节点名称（路径末段）
     * @param directory    是否目录
     * @param size         解压后大小（字节）
     * @param lastModified 条目最后修改时间（毫秒；无时间信息/合成目录为 0）
     * @param comment      头部注释摘要（无注释/目录为 null）
     */
    public record Node(String path, String name, boolean directory, long size, long lastModified, @Nullable String comment) {
    }

    /**
     * 判断压缩包是否允许索引（体积防护）
     *
     * @param file   压缩包文件
     * @param format 压缩包格式
     * @return boolean
     */
    public static boolean isIndexable(final File file, final ArchiveFormats format) {
        final long limit = format.isTarFamily() ? MAX_ARCHIVE_SIZE_TAR : MAX_ARCHIVE_SIZE_STREAM;
        return file.length() <= limit;
    }

    /**
     * 构建压缩包索引
     *
     * @param file   压缩包文件
     * @param format 压缩包格式
     * @return 索引；条目数超限时返回 null
     * @throws IOException 读取失败
     */
    public static ArchiveIndex build(final File file, final ArchiveFormats format) throws IOException {
        final List<ArchiveFormats.RawEntry> rawEntries = format.readEntries(file);
        if (rawEntries.size() > MAX_ENTRIES) {
            return null;
        }
        // 先按路径登记节点，并合成缺失的祖先目录（zip 常常不含显式目录条目）
        final Map<String, Node> nodesByPath = new java.util.LinkedHashMap<>();
        for (final ArchiveFormats.RawEntry raw : rawEntries) {
            // 归一化路径分隔符并去除目录尾部斜杠
            final String normalized = raw.path().replace('\\', '/');
            final String path = raw.directory() && normalized.endsWith("/")
                    ? normalized.substring(0, normalized.length() - 1)
                    : normalized;
            if (path.isEmpty()) {
                continue;
            }
            final String name = path.substring(path.lastIndexOf('/') + 1);
            nodesByPath.putIfAbsent(path, new Node(path, name, raw.directory(), raw.size(), raw.lastModified(), raw.comment()));
            // 逐层合成祖先目录节点
            String ancestor = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            while (!ancestor.isEmpty()) {
                final String ancestorName = ancestor.substring(ancestor.lastIndexOf('/') + 1);
                nodesByPath.putIfAbsent(ancestor, new Node(ancestor, ancestorName, true, 0, 0L, null));
                ancestor = ancestor.contains("/") ? ancestor.substring(0, ancestor.lastIndexOf('/')) : "";
            }
        }
        // 按父路径分组建树
        final Map<String, List<Node>> childrenByParent = new HashMap<>();
        for (final Node node : nodesByPath.values()) {
            final String parent = node.path().contains("/")
                    ? node.path().substring(0, node.path().lastIndexOf('/'))
                    : ROOT_PARENT;
            childrenByParent.computeIfAbsent(parent, _ -> new ArrayList<>()).add(node);
        }
        // 目录在前、名称字典序
        childrenByParent.values().forEach(children -> children.sort(
                Comparator.comparing(Node::directory).reversed().thenComparing(Node::name)
        ));
        return new ArchiveIndex(childrenByParent, rawEntries.size());
    }

    /**
     * 获取指定父路径下的子层条目
     *
     * @param parentPath 父路径（根层传 {@link #ROOT_PARENT}）
     * @return 子条目列表（目录在前、按名称排序），无子层返回空列表
     */
    public List<Node> childrenOf(final String parentPath) {
        final List<Node> children = this.childrenByParent.get(parentPath);
        return Objects.nonNull(children) ? children : List.of();
    }

    /**
     * 按路径查找条目
     *
     * @param path 条目路径
     * @return 条目；不存在返回 null
     */
    public Node find(final String path) {
        final int slashIndex = path.lastIndexOf('/');
        final String parent = slashIndex >= 0 ? path.substring(0, slashIndex) : ROOT_PARENT;
        for (final Node node : this.childrenOf(parent)) {
            if (node.path().equals(path)) {
                return node;
            }
        }
        return null;
    }

    /**
     * 条目总数
     *
     * @return int
     */
    public int entryCount() {
        return this.entryCount;
    }

    /**
     * 全部条目（供搜索遍历）
     *
     * @return 全部条目列表
     */
    public List<Node> allNodes() {
        return this.childrenByParent.values().stream().flatMap(List::stream).toList();
    }
}
