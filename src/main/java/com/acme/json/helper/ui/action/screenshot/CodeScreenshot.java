package com.acme.json.helper.ui.action.screenshot;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.notice.Notifier;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 代码截图
 * @author 拒绝者
 * @date 2025-11-06
 */
public final class CodeScreenshot extends AnAction {
    /**
     * 加载资源文件
     */
    private static final ResourceBundle bundle = ResourceBundle.getBundle("messages.JsonHelperBundle");

    /**
     * 创建图像
     * @param editor 编辑
     * @return {@link BufferedImage }
     */
    public static BufferedImage createImage(final Editor editor) {
        return Opt.ofNullable(editor)
                .map(item -> {
                    // 选区控制器
                    final SelectionModel selection = item.getSelectionModel();
                    // 选区范围
                    final int end = selection.getSelectionEnd();
                    final int start = selection.getSelectionStart();
                    // 移除选区高亮
                    selection.removeSelection();
                    // 编辑器片段组件
                    final EditorFragmentComponent component = EditorFragmentComponent.createEditorFragmentComponent(
                            item,
                            item.offsetToLogicalPosition(start).line,
                            item.offsetToLogicalPosition(end).line,
                            Boolean.TRUE, Boolean.FALSE
                    );
                    // 清空组件边框并设置内边距
                    component.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
                    // 截图组件
                    final JWindow window = new JWindow();
                    window.getContentPane().add(component);
                    window.pack();
                    window.setLocation(-9999, -9999);
                    window.setSize(component.getPreferredSize());
                    window.setVisible(Boolean.TRUE);
                    // 绘制截图
                    final BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    final Graphics2D graphics = image.createGraphics();
                    component.paint(graphics);
                    graphics.dispose();
                    window.dispose();
                    selection.setSelection(start, end);
                    return image;
                })
                .orElse(null);
    }

    /**
     * 尝试复制到剪贴板
     * @param image 图像
     * @return boolean
     */
    private static boolean tryCopyToClipboard(final BufferedImage image) {
        if (!SwingUtilities.isEventDispatchThread()) {
            return Boolean.FALSE;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new Transferable() {
                        @Override
                        public DataFlavor[] getTransferDataFlavors() {
                            return new DataFlavor[]{DataFlavor.imageFlavor};
                        }

                        @Override
                        public boolean isDataFlavorSupported(final DataFlavor flavor) {
                            return flavor == DataFlavor.imageFlavor;
                        }

                        @Override
                        public @NotNull Object getTransferData(final DataFlavor flavor) {
                            return image;
                        }
                    }, null);
            return Boolean.TRUE;
        } catch (final Exception e) {
            return Boolean.FALSE;
        }
    }

    /**
     * 剪贴板失败时提示用户保存文件
     * @param image   图像
     * @param project 项目
     */
    private static void showClipboardFailedNotification(final BufferedImage image, final Project project) {
        Notifier.notifyWarn(
                bundle.getString("show.clipboard.failed.notification"), project,
                NotificationAction.create(bundle.getString("show.clipboard.notification.action"), (e, n) -> saveToFile(image, project))
        );
    }

    /**
     * 剪贴板成功时提示用户保存文件
     * @param image   图像
     * @param project 项目
     */
    private static void showCopySuccessNotification(final BufferedImage image, final Project project) {
        Notifier.notifyInfo(
                bundle.getString("show.copy.success.notification"), project,
                NotificationAction.create(bundle.getString("show.clipboard.notification.action"), (e, n) -> saveToFile(image, project))
        );
    }

    /**
     * 保存到文件
     * @param image   图像
     * @param project 项目
     */
    private static void saveToFile(final BufferedImage image, final Project project) {
        Opt.ofNullable(
                        FileChooserFactory.getInstance()
                                .createSaveFileDialog(
                                        new FileSaverDescriptor(bundle.getString("show.clipboard.file.saver.descriptor"), "", "png"), project
                                ).save(buildDefaultFileName())
                ).map(wrapper -> Objects.requireNonNull(wrapper).getFile())
                .ifPresent(file -> {
                    try (final FileOutputStream fos = new FileOutputStream(file)) {
                        ImageIO.write(image, "png", fos);
                    } catch (final Exception t) {
                        Notifier.notifyError("Failed to write file: %s".formatted(t.getClass().getSimpleName()), project);
                    }
                });
    }

    /**
     * 生成默认文件名: screenshot-{fileName}-{yyyyMMddHHmmss}.png
     * @return {@link String }
     */
    private static String buildDefaultFileName() {
        return StrUtil.format("screenshot-{}.png", DatePattern.PURE_DATETIME_FORMATTER.format(LocalDateTime.now()));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        Opt.ofNullable(event.getProject())
                .flatMap(project ->
                        Opt.ofNullable(PlatformDataKeys.EDITOR.getData(event.getDataContext()))
                                .filter(editor -> editor.getSelectionModel().hasSelection())
                                .map(editor -> takeSnapshot(editor, project))
                ).orElseGet(() -> {
                    Notifier.notifyError(bundle.getString("show.clipboard.select.failed.notification"), event.getProject());
                    return Opt.empty();
                });
    }

    /**
     * 拍快照
     * @param editor  编辑器
     * @param project 项目
     * @return {@link Opt}<{@link BufferedImage}>
     */
    private Opt<BufferedImage> takeSnapshot(final Editor editor, final Project project) {
        return Opt.ofNullable(createImage(editor)).filter(Objects::nonNull)
                .map(image -> {
                    if (tryCopyToClipboard(image)) {
                        showCopySuccessNotification(image, project);
                    } else {
                        showClipboardFailedNotification(image, project);
                    }
                    return image;
                });
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        event.getPresentation().setEnabled(
                Opt.ofNullable(event.getProject())
                        .flatMap(project -> Opt.ofNullable(PlatformDataKeys.EDITOR.getData(event.getDataContext())))
                        .map(editor -> editor.getSelectionModel().hasSelection()).orElse(Boolean.FALSE)
        );
    }
}