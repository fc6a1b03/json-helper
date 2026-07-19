package com.acme.json.helper.core.archive;

import com.acme.json.helper.core.settings.PluginSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * 压缩包文件图标提供者
 * <p>
 * 将项目树中压缩包文件（zip/7z/jar/tar 等）的图标统一为平台归档图标，形成一致视觉识别
 *
 * @author 拒绝者
 * @date 2026-07-19
 */
public final class ArchiveIconProvider extends IconProvider {
    @Override
    public @Nullable Icon getIcon(@NotNull final PsiElement element, final int flags) {
        try {
            // 设置面板关闭本功能时保持平台默认图标
            if (!PluginSettings.of().archiveNodeEnabled) {
                return null;
            }
            if (element instanceof final PsiFile psiFile) {
                final VirtualFile virtualFile = psiFile.getVirtualFile();
                if (Objects.nonNull(virtualFile) && !virtualFile.isDirectory() && Objects.nonNull(ArchiveFormats.of(virtualFile.getName()))) {
                    return AllIcons.FileTypes.Archive;
                }
            }
        } catch (final Exception e) {
            // 异常时返回 null，由平台回退默认图标
        }
        return null;
    }
}
