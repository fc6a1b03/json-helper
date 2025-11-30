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
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.stream.Collectors;

/**
 * HTTP请求搜索实现<br/>
 * 用于搜索和管理HTTP请求文件
 * @param project 项目信息
 * @author 拒绝者
 * @date 2025-11-05
 */
public record HttpRequestSearch(Project project) implements WeightedSearchEverywhereContributor<HttpRequestItem> {
    /**
     * 搜索缓存实例
     */
    private static final SearchCache CACHE = new SearchCache();

    @Override
    public @NotNull String getSearchProviderId() {
        return "HttpRequestSearch";
    }

    @Override
    public @NotNull String getGroupName() {
        return "HTTP";
    }

    @Override
    public int getSortWeight() {
        return 800;
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
     * @param item       要处理的HTTP请求项
     * @param modifiers  键盘修饰符
     * @param searchText 搜索文本
     * @return 始终返回 true
     */
    @Override
    public boolean processSelectedItem(@NotNull final HttpRequestItem item, final int modifiers, @NotNull final String searchText) {
        Opt.ofNullable(LocalFileSystem.getInstance().findFileByPath(item.getFilePath()))
                .ifPresent(virtualFile -> FileEditorManager.getInstance(this.project).openFile(Objects.requireNonNull(virtualFile), Boolean.TRUE));
        return Boolean.TRUE;
    }

    /**
     * 获取 HTTP 请求项的列表单元格渲染器
     * <p>
     * 返回一个用于渲染 HttpRequestItem 对象的列表单元格渲染器, 该渲染器会显示请求文件名和路径信息,<br/>
     * 并根据选中状态设置不同的背景颜色
     * @return HTTP 请求项的列表单元格渲染器
     */
    @Override
    public @NotNull ListCellRenderer<? super HttpRequestItem> getElementsRenderer() {
        return (list, value, index, isSelected, cellHasFocus) -> new SimpleColoredComponent() {{
            if (Objects.nonNull(value)) {
                this.setIcon(AllIcons.FileTypes.Http);
                this.append(value.getFileName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                this.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
                this.append("  %s".formatted(value.getFilePath()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }};
    }

    /**
     * 根据指定模式获取加权的HTTP请求文件元素
     * <p>
     * 该方法根据给定的搜索模式过滤HTTP请求文件, 并通过匹配器计算权重,<br/>
     * 最终将匹配结果传递给消费者处理器进行处理
     * @param pattern   用于匹配HTTP请求文件名的模式字符串, 不能为空
     * @param indicator 进度指示器, 用于监控操作进度, 不能为空
     * @param consumer  用于处理匹配结果的处理器, 不能为空
     */
    @Override
    public void fetchWeightedElements(@NotNull final String pattern, @NotNull final ProgressIndicator indicator, @NotNull final Processor<? super FoundItemDescriptor<HttpRequestItem>> consumer) {
        // 获取缓存中的HTTP请求文件
        final SequencedCollection<HttpRequestItem> src = CACHE.getHttp();
        if (CollUtil.isEmpty(src)) return;
        // 过滤结果 - 可以根据需要添加过滤逻辑
        final SequencedCollection<HttpRequestItem> filteredFiles = src.stream().filter(Objects::nonNull)
                .filter(item -> StrUtil.isEmpty(pattern) || StrUtil.emptyIfNull(item.getFileName()).toLowerCase().contains(pattern.toLowerCase()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 如果是空模式，直接返回所有过滤后的文件
        if (StrUtil.isEmpty(pattern)) {
            filteredFiles.stream().map(item -> new FoundItemDescriptor<>(item, 100)).forEach(consumer::process);
            return;
        }
        // 使用匹配器计算权重
        final MinusculeMatcher matcher = NameUtil.buildMatcher("*%s".formatted(pattern), NameUtil.MatchingCaseSensitivity.NONE);
        // 计算权重并排序
        filteredFiles.stream().filter(Objects::nonNull)
                .map(item -> new FoundItemDescriptor<>(item, matcher.matchingDegree(item.getFileName())))
                .filter(descriptor -> descriptor.getWeight() > 0)
                .sorted((a, b) -> Integer.compare(b.getWeight(), a.getWeight()))
                .forEach(consumer::process);
    }
}