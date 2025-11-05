package com.acme.json.helper.ui.search.item;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;

import javax.swing.*;

/**
 * 项目导航项
 * @author 拒绝者
 * @since 2025-11-05
 */
public sealed interface ProjectNavigationItem extends NavigationItem {
    /**
     * 构造已打开项目导航项
     * @param name       名字
     * @param path       路径
     * @param lastModify 上次修改
     * @return {@link ProjectNavigationItem }
     */
    static ProjectNavigationItem opened(final String name, final String path, final long lastModify) {
        return new Opened(name, path, lastModify);
    }

    /**
     * 构造最近项目导航项
     * @param name       名字
     * @param path       路径
     * @param lastModify 上次修改
     * @return {@link ProjectNavigationItem }
     */
    static ProjectNavigationItem recent(final String name, final String path, final long lastModify) {
        return new Recent(name, path, lastModify);
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
     * @return {@link ItemPresentation }
     */
    @Override
    default ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return ProjectNavigationItem.this instanceof final Opened opened
                        ? "%s (opened)".formatted(opened.projectName())
                        : "%s - %s".formatted(ProjectNavigationItem.this.projectName(), ProjectNavigationItem.this.projectPath());
            }

            @Override
            public Icon getIcon(final boolean unused) {
                return AllIcons.Nodes.Module;
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
}