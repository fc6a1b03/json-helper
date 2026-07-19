package com.acme.json.helper.core.search;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.search.cache.SearchCache;
import com.acme.json.helper.core.search.item.HttpRequestItem;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.SequencedCollection;

/**
 * HTTP请求搜索实现<br/>
 * 用于搜索和管理HTTP请求文件
 *
 * @param project 项目信息
 * @author 拒绝者
 * @date 2025-11-05
 */
public record HttpRequestSearch(Project project) implements WeightedSearchEverywhereContributor<HttpRequestItem> {
    /**
     * 搜索提供者 ID（与 HttpRequestSearchFactory / 搜索动作的引用保持一致）
     */
    public static final String PROVIDER_ID = "HttpRequestSearch";
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 排序权重
     */
    private static final int SORT_WEIGHT = 800;
    /**
     * 空模式默认权重
     */
    private static final int EMPTY_PATTERN_WEIGHT = 100;

    private SearchCache cache() {
        return SearchCache.getInstance(Objects.requireNonNull(this.project));
    }

    @Override
    public @NotNull String getSearchProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public @NotNull String getGroupName() {
        return BUNDLE.getString("http.search.group.name");
    }

    @Override
    public int getSortWeight() {
        return SORT_WEIGHT;
    }

    @Override
    public boolean isEmptyPatternSupported() {
        return Boolean.TRUE;
    }

    @Override
    public boolean isShownInSeparateTab() {
        return Boolean.TRUE;
    }

    @Override
    public boolean showInFindResults() {
        return Boolean.FALSE;
    }

    /**
     * 处理选中的HTTP请求文件
     * <p>
     * 根据文件路径打开或跳转到已打开的HTTP请求文件
     *
     * @param item       要处理的HTTP请求项
     * @param modifiers  键盘修饰符
     * @param searchText 搜索文本
     * @return 始终返回 true
     */
    @Override
    public boolean processSelectedItem(@NotNull final HttpRequestItem item, final int modifiers, @NotNull final String searchText) {
        Opt.ofNullable(LocalFileSystem.getInstance().findFileByPath(item.filePath()))
                .ifPresent(virtualFile -> FileEditorManager.getInstance(this.project).openFile(Objects.requireNonNull(virtualFile), Boolean.TRUE));
        return Boolean.TRUE;
    }

    /**
     * 获取 HTTP 请求项的列表单元格渲染器
     *
     * @return HTTP 请求项的列表单元格渲染器
     */
    @Override
    public @NotNull ListCellRenderer<? super HttpRequestItem> getElementsRenderer() {
        return new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull final JList<? extends HttpRequestItem> list,
                                                 final HttpRequestItem value,
                                                 final int index,
                                                 final boolean selected,
                                                 final boolean hasFocus) {
                if (Objects.isNull(value)) {
                    return;
                }
                this.setIcon(AllIcons.FileTypes.Http);
                this.append(value.fileName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                this.append("  %s".formatted(value.filePath()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        };
    }

    /**
     * 根据指定模式获取加权的HTTP请求文件元素
     * <p>
     * 该方法根据给定的搜索模式过滤HTTP请求文件, 并通过匹配器计算权重,<br/>
     * 最终将匹配结果传递给消费者处理器进行处理
     *
     * @param pattern   用于匹配HTTP请求文件名的模式字符串, 不能为空
     * @param indicator 进度指示器, 用于监控操作进度, 不能为空
     * @param consumer  用于处理匹配结果的处理器, 不能为空
     */
    @Override
    public void fetchWeightedElements(@NotNull final String pattern, @NotNull final ProgressIndicator indicator, @NotNull final Processor<? super FoundItemDescriptor<HttpRequestItem>> consumer) {
        // 获取缓存中的HTTP请求文件
        final SequencedCollection<HttpRequestItem> src = this.cache().getHttp();
        if (CollUtil.isEmpty(src)) return;
        // 过滤结果
        final LinkedHashSet<HttpRequestItem> filteredFiles = new LinkedHashSet<>();
        final String lowerPattern = pattern.toLowerCase(Locale.ROOT);
        for (final HttpRequestItem item : src) {
            if (Objects.isNull(item)) {
                continue;
            }
            if (StrUtil.isEmpty(pattern) || StrUtil.emptyIfNull(item.fileName()).toLowerCase(Locale.ROOT).contains(lowerPattern)) {
                filteredFiles.add(item);
            }
        }
        // 如果是空模式，直接返回所有过滤后的文件
        if (StrUtil.isEmpty(pattern)) {
            filteredFiles.stream().map(item -> new FoundItemDescriptor<>(item, EMPTY_PATTERN_WEIGHT)).forEach(consumer::process);
            return;
        }
        // 使用匹配器计算权重
        final MinusculeMatcher matcher = NameUtil.buildMatcher("*%s".formatted(pattern)).build();
        // 计算权重并排序
        final List<FoundItemDescriptor<HttpRequestItem>> descriptors = new ArrayList<>(filteredFiles.size());
        for (final HttpRequestItem item : filteredFiles) {
            final int weight = matcher.matchingDegree(item.fileName());
            if (weight > 0) {
                descriptors.add(new FoundItemDescriptor<>(item, weight));
            }
        }
        descriptors.sort((left, right) -> Integer.compare(right.getWeight(), left.getWeight()));
        descriptors.forEach(consumer::process);
    }
}
