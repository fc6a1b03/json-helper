package com.acme.json.helper.core.archive;

import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.settings.PluginSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * 压缩包内容搜索
 * <p>
 * 在 Search Everywhere 中直接检索项目内压缩包（zip/7z/jar/tar 等）的条目，
 * 选中后以只读方式打开到编辑器
 *
 * @param project 项目信息
 * @author 拒绝者
 * @date 2026-07-19
 */
public record ArchiveSearch(Project project) implements WeightedSearchEverywhereContributor<ArchiveSearch.ArchiveEntryItem> {
    /**
     * 搜索提供者 ID
     */
    public static final String PROVIDER_ID = "ArchiveSearch";
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 排序权重
     */
    private static final int SORT_WEIGHT = 600;
    /**
     * 单次搜索返回的最大条目数（防止大索引下结果爆炸拖慢面板）
     */
    private static final int MAX_RESULTS = 200;
    /**
     * 内容命中项的基础权重（低于条目名匹配）
     */
    private static final int CONTENT_HIT_WEIGHT = 300;
    /**
     * 内容匹配的最短搜索词长度（过短噪声过大）
     */
    private static final int MIN_CONTENT_PATTERN_LENGTH = 3;
    /**
     * 参与内容匹配的文本类条目扩展名
     */
    static final Set<String> TEXT_ENTRY_EXTENSIONS = Set.of(
            "txt", "md", "json", "xml", "yaml", "yml", "toml", "properties", "csv", "tsv",
            "java", "kt", "kts", "groovy", "scala", "py", "js", "ts", "jsx", "tsx", "vue",
            "html", "htm", "css", "scss", "less", "sql", "sh", "bat", "ps1", "cmd",
            "c", "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php", "swift", "lua",
            "ini", "conf", "cfg", "log", "gradle", "env", "gitignore", "mf"
    );

    /**
     * 压缩包条目搜索项
     *
     * @param archivePath 所属压缩包路径
     * @param archiveName 所属压缩包名称
     * @param node        条目节点
     * @param line        内容命中行号（1 起始；0 表示不定位）
     */
    public record ArchiveEntryItem(String archivePath, String archiveName, ArchiveIndex.Node node, int line) {
    }

    private ArchiveCacheService cache() {
        return ArchiveCacheService.getInstance(Objects.requireNonNull(this.project));
    }

    @Override
    public @NotNull String getSearchProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public @NotNull String getGroupName() {
        return BUNDLE.getString("archive.search.group.name");
    }

    @Override
    public int getSortWeight() {
        return SORT_WEIGHT;
    }

    @Override
    public boolean isEmptyPatternSupported() {
        return Boolean.FALSE;
    }

    @Override
    public boolean isShownInSeparateTab() {
        // 不开独立页签，结果并入 All 页签统一呈现
        return Boolean.FALSE;
    }

    @Override
    public boolean showInFindResults() {
        return Boolean.FALSE;
    }

    /**
     * 处理选中的压缩包条目（以只读方式打开到编辑器，内容命中时定位到命中行）
     *
     * @param item       选中的条目
     * @param modifiers  键盘修饰符
     * @param searchText 搜索文本
     * @return 始终返回 true
     */
    @Override
    public boolean processSelectedItem(@NotNull final ArchiveEntryItem item, final int modifiers, @NotNull final String searchText) {
        ArchiveOpener.open(this.project, item.archivePath(), item.node(), item.line());
        return Boolean.TRUE;
    }

    /**
     * 获取条目列表渲染器
     *
     * @return 条目列表渲染器
     */
    @Override
    public @NotNull ListCellRenderer<? super ArchiveEntryItem> getElementsRenderer() {
        return new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull final JList<? extends ArchiveEntryItem> list,
                                                 final ArchiveEntryItem value,
                                                 final int index,
                                                 final boolean selected,
                                                 final boolean hasFocus) {
                if (Objects.isNull(value)) {
                    return;
                }
                this.setIcon(value.node().directory() ? AllIcons.Nodes.Folder : AllIcons.FileTypes.Any_type);
                this.append(value.node().path(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                if (value.line() > 0) {
                    // 内容命中显示行号
                    this.append(":%d".formatted(value.line()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                }
                this.append("  " + value.archiveName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        };
    }

    /**
     * 按模式检索项目内压缩包条目（条目名匹配 + 内容 trigram 索引匹配两阶段）
     *
     * @param pattern   匹配模式
     * @param indicator 进度指示器
     * @param consumer  结果处理器
     */
    @Override
    public void fetchWeightedElements(@NotNull final String pattern, @NotNull final ProgressIndicator indicator,
                                      @NotNull final Processor<? super FoundItemDescriptor<ArchiveEntryItem>> consumer) {
        // 设置面板关闭本功能时不产出结果
        if (!PluginSettings.of().archiveNodeEnabled || StrUtil.isBlank(pattern)) {
            return;
        }
        final String trimmed = pattern.trim();
        final String lowerPattern = trimmed.toLowerCase(Locale.ROOT);
        final MinusculeMatcher matcher = NameUtil.buildMatcher("*%s*".formatted(trimmed)).build();
        final boolean contentSearchEnabled = lowerPattern.length() >= MIN_CONTENT_PATTERN_LENGTH;
        final Set<String> emittedPaths = new HashSet<>();
        for (final String archivePath : this.cache().getProjectArchives(this.project)) {
            if (indicator.isCanceled() || emittedPaths.size() >= MAX_RESULTS) {
                return;
            }
            final ArchiveIndex index = this.cache().getIndex(archivePath);
            if (Objects.isNull(index)) {
                continue;
            }
            final String archiveName = new File(archivePath).getName();
            // 阶段一：条目名匹配（纯内存）
            for (final ArchiveIndex.Node node : index.allNodes()) {
                if (indicator.isCanceled() || emittedPaths.size() >= MAX_RESULTS) {
                    return;
                }
                if (node.directory() || !node.name().toLowerCase(Locale.ROOT).contains(lowerPattern)) {
                    continue;
                }
                final int weight = matcher.matchingDegree(node.name());
                if (weight > 0 && emittedPaths.add(node.path())) {
                    consumer.process(new FoundItemDescriptor<>(new ArchiveEntryItem(archivePath, archiveName, node, 0), weight));
                }
            }
            // 阶段二：内容索引匹配（trigram 倒排，索引构建一次后查询近零成本）
            if (!contentSearchEnabled) {
                continue;
            }
            final ArchiveContentIndex contentIndex = this.cache().getContentIndex(archivePath);
            if (Objects.isNull(contentIndex)) {
                continue;
            }
            for (final ArchiveContentIndex.ContentHit hit : contentIndex.query(lowerPattern)) {
                if (indicator.isCanceled() || emittedPaths.size() >= MAX_RESULTS) {
                    return;
                }
                final ArchiveIndex.Node node = index.find(hit.path());
                if (Objects.nonNull(node) && emittedPaths.add(node.path())) {
                    consumer.process(new FoundItemDescriptor<>(new ArchiveEntryItem(archivePath, archiveName, node, hit.line()), CONTENT_HIT_WEIGHT));
                }
            }
        }
    }
}
