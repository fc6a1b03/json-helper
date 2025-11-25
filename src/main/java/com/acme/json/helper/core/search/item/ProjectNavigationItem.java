package com.acme.json.helper.core.search.item;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;

import javax.swing.*;
import java.util.regex.Pattern;

/**
 * 项目导航项
 * @author 拒绝者
 * @since 2025-11-05
 */
public sealed interface ProjectNavigationItem extends NavigationItem permits ProjectNavigationItem.Opened, ProjectNavigationItem.Recent, ProjectNavigationItem.GitRepository {
    /**
     * Git URL验证正则
     */
    Pattern GIT_URL_PATTERN = Pattern.compile(
            "^(https?://|git@|ssh://|git://)([\\w.-]+@)?([\\w.-]+)(:\\d+)?(/[\\w./-]+)+(\\.git)?$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 构造已打开项目导航项
     * @param name       名字
     * @param path       路径
     * @param lastModify 上次修改
     * @return {@link ProjectNavigationItem}
     */
    static ProjectNavigationItem opened(final String name, final String path, final long lastModify) {
        return new Opened(name, path, lastModify);
    }

    /**
     * 构造最近项目导航项
     * @param name       名字
     * @param path       路径
     * @param lastModify 上次修改
     * @return {@link ProjectNavigationItem}
     */
    static ProjectNavigationItem recent(final String name, final String path, final long lastModify) {
        return new Recent(name, path, lastModify);
    }

    /**
     * 构造Git仓库导航项
     * @param repositoryUrl 仓库URL
     * @param projectName   项目名称
     * @param lastAccess    上次访问
     * @return {@link ProjectNavigationItem}
     */
    static ProjectNavigationItem gitRepository(final String repositoryUrl, final String projectName, final long lastAccess) {
        return new GitRepository(repositoryUrl, projectName, lastAccess);
    }

    /**
     * 项目名称
     */
    String projectName();

    /**
     * 项目路径
     */
    String projectPath();

    @Override
    default String getName() {
        return this.projectName();
    }

    /**
     * 获取项目列表
     * @return {@link ItemPresentation}
     */
    @Override
    default ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return switch (ProjectNavigationItem.this) {
                    case final Opened opened -> "%s (opened)".formatted(opened.projectName());
                    case final Recent recent -> "%s - %s".formatted(recent.projectName(), recent.projectPath());
                    case final GitRepository gitRepo ->
                            "%s [%s]".formatted(gitRepo.projectName(), gitRepo.repositoryUrl());
                };
            }

            @Override
            public Icon getIcon(final boolean unused) {
                return switch (ProjectNavigationItem.this) {
                    case final Opened ignored -> ExecutionUtil.getLiveIndicator(AllIcons.Nodes.Module);
                    case final Recent ignored -> AllIcons.Nodes.Module;
                    case final GitRepository ignored -> AllIcons.Vcs.Clone;
                };
            }
        };
    }

    /**
     * 已打开
     * @param projectName 项目名称
     * @param projectPath 项目路径
     * @param lastModify  最后修改时间戳
     * @author 拒绝者
     * @date 2025-11-05
     */
    record Opened(String projectName, String projectPath, long lastModify) implements ProjectNavigationItem {
    }

    /**
     * 最近打开
     * @param projectName 项目名称
     * @param projectPath 项目路径
     * @param lastModify  最后修改时间戳
     * @author 拒绝者
     * @date 2025-11-05
     */
    record Recent(String projectName, String projectPath, long lastModify) implements ProjectNavigationItem {
    }

    /**
     * Git仓库
     * @param repositoryUrl 仓库URL
     * @param projectName   项目名称
     * @param lastAccess    上次访问时间戳
     * @author 拒绝者
     * @date 2025-11-25
     */
    record GitRepository(String repositoryUrl, String projectName, long lastAccess) implements ProjectNavigationItem {
        @Override
        public String projectPath() {
            return this.repositoryUrl;
        }
    }
}