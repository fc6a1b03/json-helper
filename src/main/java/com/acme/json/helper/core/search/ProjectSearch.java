package com.acme.json.helper.core.search;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.notice.Notifier;
import com.acme.json.helper.core.search.cache.SearchCache;
import com.acme.json.helper.core.search.item.ProjectNavigationItem;
import com.acme.json.helper.core.settings.PluginSettings;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.acme.json.helper.core.search.item.ProjectNavigationItem.GIT_URL_PATTERN;

/**
 * 项目搜索
 *
 * @author 拒绝者
 * @since 2025-11-05
 */
public record ProjectSearch(Project project) implements WeightedSearchEverywhereContributor<ProjectNavigationItem> {
    /**
     * 搜索提供者 ID（与 ProjectSearchFactory / 搜索动作的引用保持一致）
     */
    public static final String PROVIDER_ID = "ProjectSearch";
    /**
     * 排序权重
     */
    private static final int SORT_WEIGHT = 799;
    /**
     * 用于存储正在克隆的仓库信息的并发哈希表
     * <p>
     * 键为仓库名称, 值表示该仓库是否正在被克隆
     */
    private static final ConcurrentHashMap<String, Boolean> CLONING_REPOS = new ConcurrentHashMap<>();
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * HTTP(S) 协议主机前缀模式
     */
    private static final Pattern HTTP_HOST_PREFIX_PATTERN = Pattern.compile("^https?://[^/]+/");
    /**
     * git@ 主机前缀模式
     */
    private static final Pattern GIT_AT_PREFIX_PATTERN = Pattern.compile("^git@[^:/]+[:/]");
    /**
     * SSH 协议主机前缀模式
     */
    private static final Pattern SSH_HOST_PREFIX_PATTERN = Pattern.compile("^ssh://[^/]+/");
    /**
     * Git 协议主机前缀模式
     */
    private static final Pattern GIT_PROTOCOL_PREFIX_PATTERN = Pattern.compile("^git://[^/]+/");
    /**
     * .git 后缀模式
     */
    private static final Pattern GIT_SUFFIX_PATTERN = Pattern.compile("\\.git$");

    /**
     * 克隆路径推导结果缓存（避免搜索热路径上重复遍历打开项目与磁盘检查）
     */
    private static final ConcurrentHashMap<String, String> CLONE_PATH_CACHE = new ConcurrentHashMap<>();

    /**
     * 计算克隆项目的路径（带缓存）
     * <p>
     * 根据给定的 URL 地址, 生成适用于项目克隆的本地路径. 该方法会从已打开的项目中获取基础路径,<br/>
     * 并对 URL 进行一系列正则替换操作以标准化路径格式.
     *
     * @param url 远程仓库的 URL 地址, 支持多种协议格式 (http,https,git,ssh 等)
     * @return 标准化后的本地克隆路径, 格式为 "基础路径 / 处理后的 URL"
     */
    private static String calculateClonePath(final String url) {
        return CLONE_PATH_CACHE.computeIfAbsent(url, ProjectSearch::doCalculateClonePath);
    }

    /**
     * 实际计算克隆项目的路径
     *
     * @param url 远程仓库的 URL 地址
     * @return 标准化后的本地克隆路径
     */
    private static String doCalculateClonePath(final String url) {
        String basePath = System.getProperty("user.home");
        for (final Project openProject : ProjectManager.getInstance().getOpenProjects()) {
            if (StrUtil.isEmpty(openProject.getBasePath())) {
                continue;
            }
            final File projectDir = FileUtil.file(openProject.getBasePath());
            if (!projectDir.exists()) {
                continue;
            }
            final File firstParent = projectDir.getParentFile();
            if (Objects.isNull(firstParent) || !firstParent.exists()) {
                continue;
            }
            final File secondParent = firstParent.getParentFile();
            if (Objects.nonNull(secondParent) && secondParent.exists()) {
                basePath = secondParent.getAbsolutePath();
                break;
            }
            basePath = firstParent.getAbsolutePath();
            break;
        }
        return "%s/%s".formatted(
                basePath,
                GIT_SUFFIX_PATTERN.matcher(
                        GIT_PROTOCOL_PREFIX_PATTERN.matcher(
                                SSH_HOST_PREFIX_PATTERN.matcher(
                                        GIT_AT_PREFIX_PATTERN.matcher(
                                                HTTP_HOST_PREFIX_PATTERN.matcher(url).replaceFirst("")
                                        ).replaceFirst("")
                                ).replaceFirst("")
                        ).replaceFirst("")
                ).replaceFirst("")
        );
    }

    @Override
    public @NotNull String getSearchProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public @NotNull String getGroupName() {
        return BUNDLE.getString("project.search.group.name");
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
        // 设置面板关闭本搜索时不显示独立页签（Search Everywhere 每次打开时重新查询）
        return PluginSettings.of().projectSearchEnabled;
    }

    @Override
    public boolean showInFindResults() {
        return Boolean.FALSE;
    }

    /**
     * 从 Git 仓库 URL 中提取项目名称
     * <p>
     * 该方法会移除 URL 末尾的.git 后缀, 然后根据最后一个斜杠的位置提取项目名称.<br/>
     * 如果没有找到斜杠, 则返回 "unknown"
     *
     * @param url Git 仓库的完整 URL 地址
     * @return 提取到的项目名称, 如果无法提取则返回 "unknown"
     */
    private static String extractProjectName(final String url) {
        final String path = GIT_SUFFIX_PATTERN.matcher(url).replaceFirst("");
        final int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : BUNDLE.getString("search.unknown.name");
    }

    private SearchCache cache() {
        return SearchCache.getInstance(Objects.requireNonNull(this.project));
    }

    /**
     * 处理选中的项目导航项
     * <p>
     * 根据不同的项目导航项类型执行相应的处理操作. 如果是 Git 仓库, 则克隆仓库;<br/>
     * 否则打开或导入指定的项目路径.
     *
     * @param item       要处理的项目导航项
     * @param modifiers  键盘修饰符
     * @param searchText 搜索文本
     * @return 始终返回 true
     */
    @Override
    public boolean processSelectedItem(@NotNull final ProjectNavigationItem item, final int modifiers, @NotNull final String searchText) {
        if (item instanceof ProjectNavigationItem.GitRepository git) {
            this.cloneIfNotExist(git.repositoryUrl());
        } else {
            // 本回调在 Search Everywhere 的写意图读锁上下文（Dispatchers.EDT + write-intent read）中执行：
            // 同步 openOrImport 会就地起模态进度，新项目打开需写锁，与持有的写意图读互等死锁（UI 冻结）。
            // 投递到 EDT 队列尾部、脱离锁上下文后再打开
            ApplicationManager.getApplication().invokeLater(
                    () -> ProjectUtil.openOrImport(item.projectPath(), null, Boolean.FALSE));
        }
        return Boolean.TRUE;
    }

    /**
     * 如果仓库未存在则克隆指定 URL 的 Git 仓库
     * <p>
     * 该方法首先检查仓库是否正在克隆中, 如果是则显示提示信息. 如果目标目录已存在, 则直接打开该项目.<br/>
     * 否则, 启动一个后台任务来执行 Git 克隆操作, 并在完成后通知用户.<br/>
     * 克隆过程中会显示进度条, 并处理克隆过程中的各种异常情况.
     *
     * @param url Git 仓库的 URL 地址
     */
    private void cloneIfNotExist(final String url) {
        // 项目名称
        final String projectName = extractProjectName(url);
        // 检查是否已经在克隆
        if (Boolean.TRUE.equals(CLONING_REPOS.putIfAbsent(url, Boolean.TRUE))) {
            Notifier.notifyInfo("%s: %s".formatted(BUNDLE.getString("clone.repository.already.info"), projectName), this.project);
            return;
        }
        final File target = FileUtil.file(calculateClonePath(url));
        // 存在直接打开项目
        if (target.exists()) {
            ProjectUtil.openOrImport(target.toPath(), null, Boolean.TRUE);
            CLONING_REPOS.remove(url);
            return;
        }
        // 启动进度对话框
        ProgressManager.getInstance().run(new Task.Backgroundable(this.project, projectName, Boolean.TRUE) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                indicator.setIndeterminate(Boolean.TRUE);
                indicator.setText(target.getAbsolutePath());
                try {
                    // 命令流程构建器
                    final Process process = new ProcessBuilder("git", "clone", "--recurse-submodules", "--progress", url, target.getName())
                            .directory(FileUtil.mkdir(target.getParentFile())).redirectErrorStream(Boolean.TRUE).start();
                    // 读取进程输出以提供详细进度
                    try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (Objects.nonNull(line = reader.readLine())) {
                            indicator.setText2(line);
                            if (indicator.isCanceled()) {
                                process.descendants().forEach(ProcessHandle::destroyForcibly);
                                process.destroyForcibly();
                                throw new ProcessCanceledException();
                            }
                        }
                    }
                    // 等待命令流程
                    final int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new RuntimeException("%s: %d".formatted(BUNDLE.getString("clone.repository.progress.exception.info"), exitCode));
                    }
                } catch (final ProcessCanceledException e) {
                    // 重新抛出以正确处理取消
                    throw e;
                } catch (final Exception e) {
                    throw new RuntimeException("%s: %s".formatted(BUNDLE.getString("clone.repository.progress.exception.info2"), e.getMessage()), e);
                }
            }

            @Override
            public void onCancel() {
                super.onCancel();
                CLONING_REPOS.remove(url);
            }

            @Override
            public void onSuccess() {
                try {
                    // 克隆成功后移出待克隆列表并失效项目缓存
                    ProjectSearch.this.cache().removeGitRepository(url);
                    ProjectUtil.openOrImport(target.toPath(), null, Boolean.TRUE);
                } finally {
                    CLONING_REPOS.remove(url);
                }
            }

            @Override
            public void onThrowable(@NotNull final Throwable error) {
                super.onThrowable(error);
                final String errorMessage;
                if (error instanceof ProcessCanceledException) {
                    errorMessage = BUNDLE.getString("clone.repository.progress.exception.info3");
                } else {
                    errorMessage = error.getMessage();
                }
                Notifier.notifyWarn(errorMessage, ProjectSearch.this.project);
                CLONING_REPOS.remove(url);
            }
        });
    }

    /**
     * 获取项目导航项的渲染器
     * <p>
     * 返回一个用于渲染项目导航项的列表单元格渲染器, 该渲染器根据不同的项目类型显示相应的图标和文本信息
     *
     * @return 项目导航项的列表单元格渲染器
     */
    @Override
    public @NotNull ListCellRenderer<? super ProjectNavigationItem> getElementsRenderer() {
        return new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull final JList<? extends ProjectNavigationItem> list,
                                                 final ProjectNavigationItem value,
                                                 final int index,
                                                 final boolean selected,
                                                 final boolean hasFocus) {
                if (Objects.isNull(value)) {
                    return;
                }
                this.setIcon(switch (value) {
                    case final ProjectNavigationItem.Opened ignored ->
                            ExecutionUtil.getLiveIndicator(AllIcons.Nodes.Module);
                    case final ProjectNavigationItem.Recent ignored -> AllIcons.Nodes.Module;
                    case final ProjectNavigationItem.GitRepository ignored -> AllIcons.Vcs.Clone;
                });
                this.append(value.projectName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                this.append(switch (value) {
                    case final ProjectNavigationItem.Opened opened -> "  %s".formatted(opened.projectPath());
                    case final ProjectNavigationItem.Recent recent -> "  %s".formatted(recent.projectPath());
                    case final ProjectNavigationItem.GitRepository gitRepo -> "  %s".formatted(gitRepo.repositoryUrl());
                }, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        };
    }

    /**
     * 根据指定模式获取加权的项目导航项元素
     * <p>
     * 该方法根据给定的搜索模式过滤缓存中的项目导航项, 并通过匹配器计算权重,<br/>
     * 最终将匹配结果传递给消费者处理器进行处理. 如果模式匹配 Git URL 模式,<br/>
     * 则会创建对应的 Git 仓库或最近打开的项目条目.
     *
     * @param pattern   用于匹配项目名称的模式字符串, 不能为空
     * @param indicator 进度指示器, 用于监控操作进度, 不能为空
     * @param consumer  用于处理匹配结果的处理器, 不能为空
     */
    @Override
    public void fetchWeightedElements(@NotNull final String pattern, @NotNull final ProgressIndicator indicator, @NotNull final Processor<? super FoundItemDescriptor<ProjectNavigationItem>> consumer) {
        // 设置面板关闭本搜索时不产出结果
        if (!PluginSettings.of().projectSearchEnabled) return;
        final SequencedCollection<ProjectNavigationItem> cachedItems = this.cache().get();
        final LinkedHashSet<ProjectNavigationItem> src = new LinkedHashSet<>();
        for (final ProjectNavigationItem item : cachedItems) {
            if (Objects.isNull(item)) {
                continue;
            }
            if (item instanceof final ProjectNavigationItem.GitRepository git
                    && FileUtil.exist(calculateClonePath(git.repositoryUrl()))) {
                continue;
            }
            src.add(item);
        }
        if (CollUtil.isEmpty(src)) return;
        if (StrUtil.isNotBlank(pattern) && GIT_URL_PATTERN.matcher(pattern).matches()) {
            final String targetPath = calculateClonePath(pattern);
            boolean projectExists = false;
            for (final ProjectNavigationItem item : src) {
                if (targetPath.equals(item.projectPath())) {
                    projectExists = true;
                    break;
                }
            }
            consumer.process(new FoundItemDescriptor<>(
                    projectExists
                            ? ProjectNavigationItem.recent(FileUtil.file(targetPath).getName(), targetPath, System.currentTimeMillis())
                            : ProjectNavigationItem.gitRepository(pattern, extractProjectName(pattern), System.currentTimeMillis()),
                    Integer.MAX_VALUE
            ));
            return;
        }
        final MinusculeMatcher matcher = NameUtil.buildMatcher("*%s".formatted(pattern)).build();
        final List<FoundItemDescriptor<ProjectNavigationItem>> descriptors = new ArrayList<>(src.size());
        for (final ProjectNavigationItem item : src) {
            final int weight = matcher.matchingDegree(switch (item) {
                case final ProjectNavigationItem.Opened opened -> "  %s".formatted(opened.projectPath());
                case final ProjectNavigationItem.Recent recent -> "  %s".formatted(recent.projectPath());
                case final ProjectNavigationItem.GitRepository repository ->
                        "  %s".formatted(repository.repositoryUrl());
            });
            if (pattern.isBlank() || weight > 0) {
                descriptors.add(new FoundItemDescriptor<>(item, weight));
            }
        }
        descriptors.sort((left, right) -> Integer.compare(right.getWeight(), left.getWeight()));
        descriptors.forEach(consumer::process);
    }
}
