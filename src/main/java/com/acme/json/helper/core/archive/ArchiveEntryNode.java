package com.acme.json.helper.core.archive;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 压缩包条目树节点
 * <p>
 * 目录节点可继续展开子层，文件节点为叶子，双击打开到编辑器（只读）
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
public final class ArchiveEntryNode extends AbstractTreeNode<ArchiveIndex.Node> {
    /**
     * 所属压缩包路径
     */
    private final String archivePath;
    /**
     * 所属索引
     */
    private final ArchiveIndex index;

    public ArchiveEntryNode(final Project project, final String archivePath, final ArchiveIndex index, final ArchiveIndex.Node node) {
        super(project, node);
        this.archivePath = archivePath;
        this.index = index;
    }

    @Override
    public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
        final ArchiveIndex.Node node = this.getValue();
        if (node == null || !node.directory()) {
            return List.of();
        }
        final List<AbstractTreeNode<?>> children = new ArrayList<>();
        for (final ArchiveIndex.Node child : this.index.childrenOf(node.path())) {
            children.add(new ArchiveEntryNode(this.myProject, this.archivePath, this.index, child));
        }
        return children;
    }

    @Override
    protected void update(@NotNull final PresentationData presentation) {
        final ArchiveIndex.Node node = this.getValue();
        if (node == null) {
            return;
        }
        presentation.setPresentableText(node.name());
        presentation.addText(node.name(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        presentation.setIcon(node.directory()
                ? AllIcons.Nodes.Folder
                : FileTypeManager.getInstance().getFileTypeByFileName(node.name()).getIcon());
    }

    @Override
    public boolean isAlwaysLeaf() {
        final ArchiveIndex.Node node = this.getValue();
        return node != null && !node.directory();
    }

    @Override
    public boolean canNavigate() {
        final ArchiveIndex.Node node = this.getValue();
        return node != null && !node.directory();
    }

    @Override
    public boolean canNavigateToSource() {
        return this.canNavigate();
    }

    @Override
    public void navigate(final boolean requestFocus) {
        final ArchiveIndex.Node node = this.getValue();
        if (node != null && !node.directory()) {
            ArchiveOpener.open(this.myProject, this.archivePath, node);
        }
    }

    @Override
    public String getName() {
        final ArchiveIndex.Node node = this.getValue();
        return node != null ? node.name() : null;
    }
}
