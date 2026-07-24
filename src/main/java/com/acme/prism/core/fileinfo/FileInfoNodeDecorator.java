package com.acme.prism.core.fileinfo;

import com.acme.prism.core.settings.PluginSettings;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 项目树文件信息节点装饰器
 * <p>
 * 在文件名右侧追加灰色辅助文本（头部注释摘要 + 修改时间）。
 * 本方法处于 EDT 渲染热路径，只做纯内存查询：缓存未命中时委托
 * {@link FileInfoCacheService} 后台懒加载，本次渲染不追加文本，零 I/O 零阻塞。
 * 注意平台渲染规则：colored text 非空时仅渲染 colored text 而忽略 presentableText，
 * 平台文件节点默认只设置 presentableText，故追加前须先补回文件名文本，
 * 且文件名使用主题默认色（REGULAR）、辅助文本使用灰色（GRAYED）
 *
 * @author 拒绝者
 * @date 2026-07-21
 */
public final class FileInfoNodeDecorator implements ProjectViewNodeDecorator, DumbAware {
    @Override
    public void decorate(@NotNull final ProjectViewNode<?> node, @NotNull final PresentationData data) {
        if (!PluginSettings.of().fileInfoNodeEnabled) {
            return;
        }
        final VirtualFile file = node.getVirtualFile();
        if (Objects.isNull(file) || file.isDirectory() || !file.isValid() || file.getFileType().isBinary()) {
            return;
        }
        final Project project = node.getProject();
        if (Objects.isNull(project)) {
            return;
        }
        final String suffix = FileInfoCacheService.getInstance(project).suffixOf(file);
        if (Objects.isNull(suffix)) {
            return;
        }
        // colored text 为空时平台渲染会退化为仅展示 colored text，
        // 必须以主题默认色补回文件名，否则追加的辅助文本会顶掉文件名
        if (data.getColoredText().isEmpty()) {
            final String name = data.getPresentableText();
            if (Objects.nonNull(name)) {
                data.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
        }
        data.addText(suffix, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
}
