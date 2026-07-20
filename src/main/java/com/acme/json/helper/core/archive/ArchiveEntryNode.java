package com.acme.json.helper.core.archive;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
    /**
     * 条目对应的平台 VFS 文件（懒解析缓存；仅 zip 系可解析）
     */
    private volatile VirtualFile virtualFile;

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

    /**
     * 条目对应的平台 VFS 文件（仅 zip 系可解析；仅缓存命中查找，不触发 refresh）
     *
     * @return 条目对应的 VFS 文件；非 zip 系或缓存未命中返回 null
     */
    @Override
    public @Nullable VirtualFile getVirtualFile() {
        if (ArchiveFormats.of(this.archivePath) != ArchiveFormats.ZIP) {
            return null;
        }
        VirtualFile file = this.virtualFile;
        if (Objects.isNull(file)) {
            final ArchiveIndex.Node node = this.getValue();
            if (Objects.isNull(node)) {
                return null;
            }
            file = JarFileSystem.getInstance().findFileByPath(
                    ArchiveFormats.zipEntryVfsPath(new File(this.archivePath), node.path()));
            this.virtualFile = file;
        }
        return file;
    }

    /**
     * 判断本节点是否为指定压缩包内的指定条目（供树定位遍历匹配）
     *
     * @param archivePath 压缩包路径
     * @param entryPath   条目路径
     * @return boolean
     */
    public boolean isSameEntry(final String archivePath, final String entryPath) {
        final ArchiveIndex.Node node = this.getValue();
        return this.archivePath.equals(archivePath) && Objects.nonNull(node) && node.path().equals(entryPath);
    }

    /**
     * 判断本节点是否为指定条目的祖先目录（供树定位遍历剪枝）
     *
     * @param archivePath 压缩包路径
     * @param entryPath   条目路径
     * @return boolean
     */
    public boolean isAncestorEntry(final String archivePath, final String entryPath) {
        final ArchiveIndex.Node node = this.getValue();
        return this.archivePath.equals(archivePath) && Objects.nonNull(node)
                && node.directory() && entryPath.startsWith(node.path() + "/");
    }
}
