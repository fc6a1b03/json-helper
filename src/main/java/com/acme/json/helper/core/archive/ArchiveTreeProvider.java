package com.acme.json.helper.core.archive;

import com.acme.json.helper.core.settings.PluginSettings;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 压缩包树结构提供者
 * <p>
 * 在项目树中将压缩包文件（zip/7z/jar/tar 等）渲染为可展开的目录节点，
 * 子层懒加载自索引缓存，展开目录节点的成本仅为内存查询
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
public final class ArchiveTreeProvider implements TreeStructureProvider, DumbAware {
    @Override
    public @NotNull Collection<AbstractTreeNode<?>> modify(@NotNull final AbstractTreeNode<?> parent,
                                                           @NotNull final Collection<AbstractTreeNode<?>> children,
                                                           final ViewSettings settings) {
        // 设置面板关闭本功能时不注入压缩包子节点
        if (!PluginSettings.of().archiveNodeEnabled) {
            return children;
        }
        try {
            // Project View 文件节点的 value 为 PSI 元素，统一经 ProjectViewNode 提取 VirtualFile
            if (!(parent instanceof final ProjectViewNode<?> viewNode)) {
                return children;
            }
            final VirtualFile virtualFile = viewNode.getVirtualFile();
            if (Objects.isNull(virtualFile) || virtualFile.isDirectory()) {
                return children;
            }
            final ArchiveFormats format = ArchiveFormats.of(virtualFile.getName());
            if (Objects.isNull(format)) {
                return children;
            }
            final ArchiveIndex index = ArchiveCacheService.getInstance(parent.getProject()).getIndex(virtualFile.getPath());
            if (Objects.isNull(index)) {
                return children;
            }
            // 压缩包节点：注入条目子节点
            final List<AbstractTreeNode<?>> entries = new ArrayList<>(children);
            for (final ArchiveIndex.Node node : index.childrenOf(ArchiveIndex.ROOT_PARENT)) {
                entries.add(new ArchiveEntryNode(parent.getProject(), virtualFile.getPath(), index, node));
            }
            return entries;
        } catch (final Exception e) {
            // 压缩包异常绝不波及项目树渲染，原样返回子节点
            return children;
        }
    }
}
