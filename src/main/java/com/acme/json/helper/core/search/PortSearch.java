package com.acme.json.helper.core.search;

import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.notice.Notifier;
import com.acme.json.helper.core.search.cache.SearchCache;
import com.acme.json.helper.core.search.item.PortSearchItem;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 端口搜索实现<br/>
 * 用于搜索本地运行的端口进程，支持点击杀死进程
 *
 * @param project 项目信息
 * @author 拒绝者
 * @date 2026-04-01
 */
public record PortSearch(Project project) implements WeightedSearchEverywhereContributor<PortSearchItem> {
    /**
     * 搜索提供者 ID（与 PortSearchFactory / 搜索动作的引用保持一致）
     */
    public static final String PROVIDER_ID = "PortSearch";
    /**
     * 日志
     */
    private static final Logger LOG = Logger.getInstance(PortSearch.class);
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 纯数字模式
     */
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
    /**
     * 应用名称展示宽度（字符数）
     */
    private static final int APP_NAME_WIDTH = 20;
    /**
     * 端口号列宽（字符数）
     */
    private static final int PORT_COLUMN_WIDTH = 8;
    /**
     * 路径展示最大长度
     */
    private static final int PATH_MAX_LENGTH = 50;
    /**
     * 右对齐基准宽度（像素）
     */
    private static final int RIGHT_PADDING_BASE = 400;
    /**
     * 最小右间距（像素）
     */
    private static final int MIN_PADDING = 20;
    /**
     * 空模式默认权重
     */
    private static final int EMPTY_PATTERN_WEIGHT = 100;
    /**
     * 杀进程命令超时（毫秒），防止系统命令挂起导致后台线程永久阻塞
     */
    private static final long PROCESS_KILL_TIMEOUT_MS = 3000;
    /**
     * 端口号精确匹配权重
     */
    private static final int EXACT_MATCH_WEIGHT = 1000;
    /**
     * 端口号前缀匹配基础权重
     */
    private static final int PREFIX_MATCH_WEIGHT = 500;
    /**
     * 端口号包含匹配基础权重
     */
    private static final int CONTAINS_MATCH_WEIGHT = 200;
    /**
     * 排序权重
     */
    private static final int SORT_WEIGHT = 700;
    /**
     * 省略号
     */
    private static final String ELLIPSIS = "...";
    /**
     * 按扩展名缓存的文件类型图标（渲染热路径避免重复查询 FileTypeManager）
     */
    private static final Map<String, Icon> ICON_CACHE = new ConcurrentHashMap<>();

    private SearchCache cache() {
        return SearchCache.getInstance(Objects.requireNonNull(this.project));
    }

    @Override
    public @NotNull String getSearchProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public @NotNull String getGroupName() {
        return BUNDLE.getString("port.search.group.name");
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
     * 处理选中的端口进程项
     * <p>
     * 点击后杀死对应的进程
     *
     * @param item       要处理的端口进程项
     * @param modifiers  键盘修饰符
     * @param searchText 搜索文本
     * @return 始终返回 true
     */
    @Override
    public boolean processSelectedItem(@NotNull final PortSearchItem item, final int modifiers, @NotNull final String searchText) {
        killProcess(item.pid(), item.appName());
        return Boolean.TRUE;
    }

    /**
     * 杀死指定进程
     *
     * @param pid     进程ID
     * @param appName 应用名称
     */
    private void killProcess(final long pid, final String appName) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                final ProcessBuilder builder;
                if (SystemInfo.isWindows) {
                    builder = new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid));
                } else {
                    builder = new ProcessBuilder("kill", "-9", String.valueOf(pid));
                }
                builder.redirectErrorStream(Boolean.TRUE);
                final Process process = builder.start();
                final boolean finished = process.waitFor(PROCESS_KILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!finished) {
                    // 超时强制终止，避免后台线程永久阻塞
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
                final int exitCode = finished ? process.exitValue() : -1;
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (exitCode == 0) {
                        Notifier.notifyInfo(
                                BUNDLE.getString("port.search.process.killed").formatted(appName, pid), this.project
                        );
                        // 刷新缓存
                        this.cache().invalidatePortCache();
                    } else {
                        Notifier.notifyError(
                                BUNDLE.getString("port.search.process.kill.failed").formatted(appName, pid), this.project
                        );
                    }
                });
            } catch (final Exception e) {
                LOG.warn("Failed to kill process", e);
                ApplicationManager.getApplication().invokeLater(() -> Notifier.notifyError(
                        BUNDLE.getString("port.search.process.kill.error").formatted(e.getMessage()), this.project
                ));
            }
        });
    }

    /**
     * 获取端口进程项的列表单元格渲染器
     * <p>
     * 使用 ColoredListCellRenderer 实现正确的样式渲染
     * 显示格式：图标 + 应用名（对齐）+ 端口（蓝色）+ 路径（灰色，右对齐）
     *
     * @return 端口进程项的列表单元格渲染器
     */
    @Override
    public @NotNull ListCellRenderer<? super PortSearchItem> getElementsRenderer() {
        return new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull final JList<? extends PortSearchItem> list,
                                                 final PortSearchItem value,
                                                 final int index,
                                                 final boolean selected,
                                                 final boolean hasFocus) {
                if (Objects.isNull(value)) {
                    return;
                }

                // 根据应用名获取文件类型图标
                this.setIcon(getIconForApp(value.appName()));

                // 应用名称（固定宽度对齐）
                final String appName = value.appName();
                final String displayName = appName.length() > APP_NAME_WIDTH
                        ? appName.substring(0, APP_NAME_WIDTH - ELLIPSIS.length()) + ELLIPSIS
                        : appName;
                this.append(padEnd(displayName), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

                // 端口号（固定宽度，右对齐，蓝色）
                this.append(padStart(":" + value.port()), new SimpleTextAttributes(
                        SimpleTextAttributes.STYLE_BOLD,
                        selected ? JBColor.CYAN : JBColor.BLUE
                ));

                // 应用路径（灰色，右对齐）
                final String appPath = value.appPath();
                final String pathText;
                if (StrUtil.isNotEmpty(appPath)) {
                    pathText = shortenPath(appPath);
                } else {
                    pathText = BUNDLE.getString("port.search.pid.fallback").formatted(value.pid());
                }
                this.appendTextPadding(Math.max(MIN_PADDING, list.getWidth() - RIGHT_PADDING_BASE));
                this.append(pathText, new SimpleTextAttributes(
                        SimpleTextAttributes.STYLE_PLAIN,
                        selected ? JBColor.LIGHT_GRAY : JBColor.GRAY
                ));
            }
        };
    }

    /**
     * 将应用名补齐到固定宽度（避免 String.format 的 Locale 开销）
     *
     * @param text 原始文本
     * @return 补齐后的文本
     */
    private static String padEnd(final String text) {
        return text.length() >= APP_NAME_WIDTH ? text : text + " ".repeat(APP_NAME_WIDTH - text.length());
    }

    /**
     * 将端口号补齐到固定宽度（避免 String.format 的 Locale 开销）
     *
     * @param text 原始文本
     * @return 补齐后的文本
     */
    private static String padStart(final String text) {
        return text.length() >= PORT_COLUMN_WIDTH ? text : " ".repeat(PORT_COLUMN_WIDTH - text.length()) + text;
    }

    /**
     * 根据应用名称获取对应的图标
     * <p>
     * 优先从文件类型图标库获取（按扩展名缓存），失败时返回绿色执行图标
     *
     * @param appName 应用名称（如 java.exe）
     * @return 对应的图标
     */
    private static Icon getIconForApp(final String appName) {
        final int dotIndex = appName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return AllIcons.Actions.Execute;
        }
        final String ext = appName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return ICON_CACHE.computeIfAbsent(ext, key -> {
            try {
                final Icon icon = FileTypeManager.getInstance().getFileTypeByExtension(key).getIcon();
                return Objects.nonNull(icon) ? icon : AllIcons.Actions.Execute;
            } catch (final Exception ignored) {
                // 获取图标失败时使用默认图标
                return AllIcons.Actions.Execute;
            }
        });
    }

    /**
     * 缩短路径显示
     *
     * @param path 原始路径
     * @return 缩短后的路径
     */
    private static String shortenPath(final String path) {
        if (StrUtil.isEmpty(path) || path.length() <= PortSearch.PATH_MAX_LENGTH) {
            return path;
        }
        // 显示路径开头和结尾，中间用省略号
        final int half = (PortSearch.PATH_MAX_LENGTH - ELLIPSIS.length()) / 2;
        return path.substring(0, half) + ELLIPSIS + path.substring(path.length() - half);
    }

    /**
     * 根据指定模式获取加权的端口进程元素
     * <p>
     * 支持通过数字模糊搜索端口
     *
     * @param pattern   用于匹配的模式字符串
     * @param indicator 进度指示器
     * @param consumer  用于处理匹配结果的处理器
     */
    @Override
    public void fetchWeightedElements(
            @NotNull final String pattern, @NotNull final ProgressIndicator indicator, @NotNull final Processor<? super FoundItemDescriptor<PortSearchItem>> consumer
    ) {
        final String lowerPattern = pattern.toLowerCase(Locale.ROOT);
        final boolean isEmptyPattern = StrUtil.isEmpty(pattern);
        final boolean isDigitPattern = DIGIT_PATTERN.matcher(pattern).matches();
        // 根据是否有搜索词选择数据源：
        // - 空搜索：只显示 IDEA 子进程
        // - 有搜索词：搜索整个系统端口
        final List<PortSearchItem> items = isEmptyPattern ? this.cache().getIdeaChildPorts() : this.cache().getPorts();
        if (items.isEmpty()) return;
        // 过滤结果
        final List<PortSearchItem> filteredItems = new ArrayList<>();
        for (final PortSearchItem item : items) {
            if (indicator.isCanceled()) return;
            if (Objects.isNull(item)) continue;
            if (isEmptyPattern) {
                // 空模式时显示所有 IDEA 子进程
                filteredItems.add(item);
            } else if (isDigitPattern) {
                // 数字模式：匹配端口号
                if (String.valueOf(item.port()).contains(pattern)) {
                    filteredItems.add(item);
                }
            } else {
                // 文本模式：匹配应用名称
                if (item.appName().toLowerCase(Locale.ROOT).contains(lowerPattern)) {
                    filteredItems.add(item);
                }
            }
        }
        // 计算权重并排序
        final List<FoundItemDescriptor<PortSearchItem>> descriptors = new ArrayList<>(filteredItems.size());
        if (isEmptyPattern) {
            // 空模式：按端口号排序
            filteredItems.sort(Comparator.comparingInt(PortSearchItem::port));
            for (final PortSearchItem item : filteredItems) {
                descriptors.add(new FoundItemDescriptor<>(item, EMPTY_PATTERN_WEIGHT));
            }
        } else {
            // 使用匹配器计算权重
            final var matcher = NameUtil.buildMatcher("*%s*".formatted(pattern)).build();
            for (final PortSearchItem item : filteredItems) {
                int weight = 0;
                if (isDigitPattern) {
                    // 数字匹配：端口号越接近权重越高
                    final String portStr = String.valueOf(item.port());
                    if (portStr.equals(pattern)) {
                        weight = EXACT_MATCH_WEIGHT;
                    } else if (portStr.startsWith(pattern)) {
                        weight = PREFIX_MATCH_WEIGHT + pattern.length() * 10;
                    } else if (portStr.contains(pattern)) {
                        weight = CONTAINS_MATCH_WEIGHT + pattern.length() * 5;
                    }
                } else {
                    // 文本匹配
                    weight = matcher.matchingDegree(item.appName());
                }
                if (weight > 0) {
                    descriptors.add(new FoundItemDescriptor<>(item, weight));
                }
            }
            descriptors.sort((left, right) -> Integer.compare(right.getWeight(), left.getWeight()));
        }
        for (final FoundItemDescriptor<PortSearchItem> descriptor : descriptors) {
            if (indicator.isCanceled()) return;
            consumer.process(descriptor);
        }
    }
}
