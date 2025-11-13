package com.acme.json.helper.core.screenshot;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.notice.Notifier;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.notification.NotificationAction;
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
 * 代码截图供应商
 * 提供代码截图功能，支持主题适配和文件保存
 * @author 拒绝者
 * @date 2025-11-06
 */
public class CodeScreenshotSupplier {
    /**
     * 加载资源文件
     * 用于获取国际化字符串
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");

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
                    // 结束行
                    final int endLine = item.offsetToLogicalPosition(end).line;
                    // 编辑器片段组件
                    final EditorFragmentComponent component = EditorFragmentComponent.createEditorFragmentComponent(
                            item,
                            item.offsetToLogicalPosition(start).line,
                            (start != end) ? endLine + 1 : endLine,
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
     * 尝试复制图像到剪贴板
     * 将BufferedImage对象复制到系统剪贴板
     * @param image 要复制的图像
     * @return 复制是否成功
     */
    public static boolean tryCopyToClipboard(final BufferedImage image) {
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
     * 当复制到剪贴板失败时，显示通知并提供保存文件的选项
     * @param image   图像
     * @param project 项目
     */
    public static void showClipboardFailedNotification(final BufferedImage image, final Project project) {
        Notifier.notifyWarn(
                BUNDLE.getString("show.clipboard.failed.notification"), project,
                NotificationAction.create(BUNDLE.getString("show.clipboard.notification.action"), (e, n) -> saveToFile(image, project))
        );
    }

    /**
     * 剪贴板成功时提示用户保存文件
     * 当成功复制到剪贴板时，显示通知并提供保存文件的选项
     * @param image   图像
     * @param project 项目
     */
    public static void showCopySuccessNotification(final BufferedImage image, final Project project) {
        Notifier.notifyInfo(
                BUNDLE.getString("show.copy.success.notification"), project,
                NotificationAction.create(BUNDLE.getString("show.clipboard.notification.action"), (e, n) -> saveToFile(image, project))
        );
    }

    /**
     * 保存图像到文件
     * 提供文件保存对话框，将图像保存为PNG格式
     * @param image   图像
     * @param project 项目
     */
    private static void saveToFile(final BufferedImage image, final Project project) {
        Opt.ofNullable(
                        FileChooserFactory.getInstance()
                                .createSaveFileDialog(
                                        new FileSaverDescriptor(BUNDLE.getString("show.clipboard.file.saver.descriptor"), "", "png"), project
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
     * 生成默认文件名
     * 创建格式为 screenshot-{yyyyMMddHHmmss}.png 的文件名
     * @return 默认文件名字符串
     */
    private static String buildDefaultFileName() {
        return StrUtil.format("screenshot-{}.png", DatePattern.PURE_DATETIME_FORMATTER.format(LocalDateTime.now()));
    }
}