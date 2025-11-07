package com.acme.json.helper.core.search;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Opt;
import com.acme.json.helper.core.search.cache.SearchCache;
import com.acme.json.helper.core.search.item.ProjectNavigationItem;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.SequencedCollection;
import java.util.function.Supplier;

/**
 * 项目搜索
 * @author 拒绝者
 * @since 2025-11-05
 */
public final class ProjectSearch implements WeightedSearchEverywhereContributor<ProjectNavigationItem> {
    /**
     * 加载资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 项目缓存
     */
    private final Supplier<SequencedCollection<ProjectNavigationItem>> cache;

    /**
     * 项目搜索
     */
    public ProjectSearch() {
        this.cache = new SearchCache();
    }

    /**
     * 搜索器
     */
    @Override
    public @NotNull String getSearchProviderId() {
        return "ProjectSearch";
    }

    /**
     * 分组名
     * @return {@link String }
     */
    @Override
    public @NotNull String getGroupName() {
        return BUNDLE.getString("project.search.group.name");
    }

    /**
     * 排序权重
     * @return int
     */
    @Override
    public int getSortWeight() {
        return 799;
    }

    /**
     * 是否支持空搜索
     * @return boolean
     */
    @Override
    public boolean isEmptyPatternSupported() {
        return Boolean.TRUE;
    }

    /**
     * 是否独立标签
     * @return boolean
     */
    @Override
    public boolean isShownInSeparateTab() {
        return Boolean.TRUE;
    }

    /**
     * 查找结果中显示
     * @return boolean
     */
    @Override
    public boolean showInFindResults() {
        return Boolean.FALSE;
    }

    /**
     * 选中即打开/激活：已打开项目仅激活窗口，未打开项目走加载
     * @param item       项目
     * @param modifiers  修饰符
     * @param searchText 搜索文本
     * @return boolean
     */
    @Override
    public boolean processSelectedItem(@NotNull final ProjectNavigationItem item, final int modifiers, @NotNull final String searchText) {
        ProjectUtil.openOrImport(item.projectPath(), null, Boolean.FALSE);
        return Boolean.TRUE;
    }

    /**
     * 列表渲染器：已打开项目前插 live 图标，其余仅显示名称与路径
     * @return {@link ListCellRenderer }<{@link ? } {@link super } {@link ProjectNavigationItem }>
     */
    @Override
    public @NotNull ListCellRenderer<? super ProjectNavigationItem> getElementsRenderer() {
        return (list, value, index, isSel, cellHasFocus) -> new SimpleColoredComponent() {{
            Opt.ofNullable(value).ifPresent(item -> {
                this.setIcon(switch (item) {
                    case final ProjectNavigationItem.Opened ignored ->
                            ExecutionUtil.getLiveIndicator(AllIcons.Nodes.Module);
                    case final ProjectNavigationItem.Recent ignored -> AllIcons.Nodes.Module;
                });
                this.append(item.projectName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                this.append("  %s".formatted(item instanceof final ProjectNavigationItem.Recent r ? r.projectPath() : ""), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            });
            this.setBackground(isSel ? list.getSelectionBackground() : list.getBackground());
        }};
    }

    /**
     * 获取匹配到的项目
     * @param pattern   模式
     * @param indicator 指标
     * @param consumer  消费者
     */
    @Override
    public void fetchWeightedElements(@NotNull final String pattern,
                                      @NotNull final ProgressIndicator indicator,
                                      @NotNull final Processor<? super FoundItemDescriptor<ProjectNavigationItem>> consumer) {
        // 获取项目列表缓存
        final SequencedCollection<ProjectNavigationItem> src = pattern.isBlank() ? new SearchCache().load() : this.cache.get();
        if (CollUtil.isEmpty(src)) return;
        // 模糊匹配器
        final MinusculeMatcher matcher = NameUtil.buildMatcher("*%s".formatted(pattern), NameUtil.MatchingCaseSensitivity.NONE);
        // 加载列表
        src.stream().filter(Objects::nonNull)
                .map(item -> new FoundItemDescriptor<>(item, matcher.matchingDegree(switch (item) {
                    case final ProjectNavigationItem.Opened opened -> opened.projectName();
                    case final ProjectNavigationItem.Recent recent ->
                            "%s %s".formatted(recent.projectName(), recent.projectPath());
                })))
                // 空串旁路过滤
                .filter(d -> pattern.isBlank() || d.getWeight() > 0)
                .sorted((a, b) -> Integer.compare(b.getWeight(), a.getWeight()))
                .forEach(consumer::process);
    }
}