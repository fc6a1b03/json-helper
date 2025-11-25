package com.acme.json.helper.core.search;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.notice.Notifier;
import com.acme.json.helper.core.search.cache.SearchCache;
import com.acme.json.helper.core.search.item.ProjectNavigationItem;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.acme.json.helper.core.search.item.ProjectNavigationItem.GIT_URL_PATTERN;

/**
 * 项目搜索
 * @author 拒绝者
 * @since 2025-11-05
 */
public record ProjectSearch(Project project) implements WeightedSearchEverywhereContributor<ProjectNavigationItem> {
    /**
     * 搜索缓存实例
     */
    private static final SearchCache CACHE = new SearchCache();
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");

    @Override
    public @NotNull String getSearchProviderId() {
        return "ProjectSearch";
    }

    @Override
    public @NotNull String getGroupName() {
        return BUNDLE.getString("project.search.group.name");
    }

    @Override
    public int getSortWeight() {
        return 799;
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
     * 计算克隆项目的路径
     * <p>
     * 根据给定的 URL 地址, 生成适用于项目克隆的本地路径. 该方法会从已打开的项目中获取基础路径,<br/>
     * 并对 URL 进行一系列正则替换操作以标准化路径格式.
     * @param url 远程仓库的 URL 地址, 支持多种协议格式 (http,https,git,ssh 等)
     * @return 标准化后的本地克隆路径, 格式为 "基础路径 / 处理后的 URL"
     */
    private static String calculateClonePath(final String url) {
        return "%s/%s".formatted(
                Arrays.stream(ProjectManager.getInstance().getOpenProjects())
                        .findFirst().filter(project -> StrUtil.isNotEmpty(project.getBasePath()))
                        .map(Project::getBasePath).filter(StrUtil::isNotEmpty)
                        .map(FileUtil::file).filter(File::exists).map(File::getParentFile)
                        .filter(File::exists).map(File::getParentFile).filter(File::exists)
                        .map(File::getAbsolutePath).orElseGet(() -> System.getProperty("user.home")),
                url.replaceFirst("^https?://[^/]+/", "")
                        .replaceFirst("^git@[^:/]+[:/]", "")
                        .replaceFirst("^ssh://[^/]+/", "")
                        .replaceFirst("^git://[^/]+/", "")
                        .replaceFirst("\\.git$", "")
        );
    }

    /**
     * 从 Git 仓库 URL 中提取项目名称
     * <p>
     * 该方法会移除 URL 末尾的.git 后缀, 然后根据最后一个斜杠的位置提取项目名称.<br/>
     * 如果没有找到斜杠, 则返回 "unknown"
     * @param url Git 仓库的完整 URL 地址
     * @return 提取到的项目名称, 如果无法提取则返回 "unknown"
     */
    private static String extractProjectName(final String url) {
        final String path = url.replaceFirst("\\.git$", "");
        final int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : "unknown";
    }

    /**
     * 处理选中的项目导航项
     * <p>
     * 根据不同的项目导航项类型执行相应的处理操作. 如果是 Git 仓库, 则克隆仓库;<br/>
     * 否则打开或导入指定的项目路径.
     * @param item       要处理的项目导航项
     * @param modifiers  键盘修饰符
     * @param searchText 搜索文本
     * @return 始终返回 true
     */
    @Override
    public boolean processSelectedItem(@NotNull final ProjectNavigationItem item, final int modifiers, @NotNull final String searchText) {
        switch (item) {
            case final ProjectNavigationItem.GitRepository git -> this.cloneIfNotExist(git.repositoryUrl());
            default -> ProjectUtil.openOrImport(item.projectPath(), null, Boolean.FALSE);
        }
        return Boolean.TRUE;
    }

    /**
     * 如果目标路径不存在, 则克隆指定 URL 的 Git 仓库
     * <p>
     * 该方法首先检查指定 URL 的仓库是否已存在本地缓存中. 如果存在, 则直接打开或导入该项目;<br/>
     * 否则, 会在后台线程中执行 Git 克隆操作. 克隆成功后会将仓库信息添加到缓存中,<br/>
     * 并在 UI 线程中打开或导入该项目; 如果克隆失败, 则显示警告通知.
     * @param url Git 仓库的 URL 地址
     */
    private void cloneIfNotExist(final String url) {
        final File target = FileUtil.file(calculateClonePath(url));
        if (target.exists()) {
            ProjectUtil.openOrImport(target.toPath(), null, Boolean.TRUE);
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                final int exitCode = new ProcessBuilder("git", "clone", url, target.getName())
                        .directory(FileUtil.mkdir(FileUtil.mkdir(target.getParentFile()))).redirectErrorStream(Boolean.TRUE).start().waitFor();
                if (exitCode == 0) {
                    CACHE.addGitRepository(url);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        ProjectUtil.openOrImport(target.toPath(), null, Boolean.TRUE);
                        CACHE.removeGitRepository(url);
                    });
                }
            } catch (final Exception e) {
                ApplicationManager.getApplication().invokeLater(() -> Notifier.notifyWarn(e.getMessage(), this.project));
            }
        });
    }

    /**
     * 获取项目导航项的渲染器
     * <p>
     * 返回一个用于渲染项目导航项的列表单元格渲染器, 该渲染器根据不同的项目类型显示相应的图标和文本信息
     * @return 项目导航项的列表单元格渲染器
     */
    @Override
    public @NotNull ListCellRenderer<? super ProjectNavigationItem> getElementsRenderer() {
        return (list, value, index, isSel, cellHasFocus) -> new SimpleColoredComponent() {{
            if (Objects.nonNull(value)) {
                this.setIcon(switch (value) {
                    case final ProjectNavigationItem.Opened ignored ->
                            ExecutionUtil.getLiveIndicator(AllIcons.Nodes.Module);
                    case final ProjectNavigationItem.Recent ignored -> AllIcons.Nodes.Module;
                    case final ProjectNavigationItem.GitRepository ignored -> AllIcons.Vcs.Clone;
                });
                this.append(value.projectName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                this.append(switch (value) {
                    case final ProjectNavigationItem.Opened opened -> "  %s".formatted(opened.projectName());
                    case final ProjectNavigationItem.Recent recent -> "  %s".formatted(recent.projectPath());
                    case final ProjectNavigationItem.GitRepository gitRepo -> "  %s".formatted(gitRepo.repositoryUrl());
                }, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                this.setBackground(isSel ? list.getSelectionBackground() : list.getBackground());
            }
        }};
    }

    /**
     * 根据指定模式获取加权的项目导航项元素
     * <p>
     * 该方法根据给定的搜索模式过滤缓存中的项目导航项, 并通过匹配器计算权重,<br/>
     * 最终将匹配结果传递给消费者处理器进行处理. 如果模式匹配 Git URL 模式,<br/>
     * 则会创建对应的 Git 仓库或最近打开的项目条目.
     * @param pattern   用于匹配项目名称的模式字符串, 不能为空
     * @param indicator 进度指示器, 用于监控操作进度, 不能为空
     * @param consumer  用于处理匹配结果的处理器, 不能为空
     */
    @Override
    public void fetchWeightedElements(@NotNull final String pattern, @NotNull final ProgressIndicator indicator, @NotNull final Processor<? super FoundItemDescriptor<ProjectNavigationItem>> consumer) {
        final SequencedCollection<ProjectNavigationItem> src = CACHE.get().stream().filter(Objects::nonNull)
                .filter(item -> switch (item) {
                    case final ProjectNavigationItem.GitRepository git ->
                            !FileUtil.exist(calculateClonePath(git.repositoryUrl()));
                    default -> Boolean.TRUE;
                }).collect(Collectors.toCollection(LinkedHashSet::new));
        if (CollUtil.isEmpty(src)) return;
        if (StrUtil.isNotBlank(pattern) && GIT_URL_PATTERN.matcher(pattern).matches()) {
            final String targetPath = calculateClonePath(pattern);
            consumer.process(new FoundItemDescriptor<>(
                    src.stream().filter(Objects::nonNull)
                            .anyMatch(item -> targetPath.equals(item.projectPath())) ?
                            ProjectNavigationItem.recent(FileUtil.file(targetPath).getName(), targetPath, System.currentTimeMillis()) :
                            ProjectNavigationItem.gitRepository(pattern, extractProjectName(pattern), System.currentTimeMillis()),
                    Integer.MAX_VALUE
            ));
            return;
        }
        final MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
        src.stream().filter(Objects::nonNull)
                .map(item -> new FoundItemDescriptor<>(item, matcher.matchingDegree(switch (item) {
                    case final ProjectNavigationItem.Opened opened -> "  %s".formatted(opened.projectName());
                    case final ProjectNavigationItem.Recent recent -> "  %s".formatted(recent.projectPath());
                    case final ProjectNavigationItem.GitRepository repository ->
                            "  %s".formatted(repository.repositoryUrl());
                })))
                .filter(descriptor -> pattern.isBlank() || descriptor.getWeight() > 0)
                .sorted((a, b) -> Integer.compare(b.getWeight(), a.getWeight()))
                .forEach(consumer::process);
    }
}