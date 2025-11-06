package com.acme.json.helper.core.screenshot;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.notice.Notifier;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 代码截图供应商
 * @author xuhaifeng
 * @date 2025-11-06
 */
public class CodeScreenshotSupplier {
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
                    // 设置组件尺寸
                    component.setSize(component.getPreferredSize());
                    // 清空组件边框
                    component.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
                    // 截图装载组件
                    final JWindow window = new JWindow();
                    window.getContentPane().add(component);
                    window.pack();
                    window.setLocation(-9999, -9999);
                    window.setSize(component.getPreferredSize());
                    window.setVisible(Boolean.TRUE);
                    // 整体尺寸
                    final int SHADOW = 8;
                    final int HEADER = 36;
                    final int RADIUS = 12;
                    final int CONTENT_W = component.getWidth();
                    final int CONTENT_H = component.getHeight();
                    final int TOTAL_W = CONTENT_W + SHADOW * 2;
                    final int TOTAL_H = HEADER + CONTENT_H + SHADOW * 2;
                    // 画布
                    final BufferedImage image = new BufferedImage(TOTAL_W, TOTAL_H, BufferedImage.TYPE_INT_ARGB);
                    final Graphics2D graphics = image.createGraphics();
                    // 绘制抗锯齿
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // 主题色
                    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
                    final Color background = scheme.getDefaultBackground();
                    final Color foreground = scheme.getDefaultForeground();
                    final boolean isDark = (background.getRed() + background.getGreen() + background.getBlue()) < 3 * 128;
                    final Color windowBg = isDark ? background.brighter() : background.darker();
                    final Color border = new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 40);
                    // 阴影
                    final RadialGradientPaint shadowPaint = new RadialGradientPaint(
                            TOTAL_W / 2f, TOTAL_H / 2f,
                            Math.max(TOTAL_W, TOTAL_H) / 2f,
                            new float[]{0.75f, 1f},
                            new Color[]{new Color(0, 0, 0, isDark ? 120 : 80), new Color(0, true)}
                    );
                    graphics.setPaint(shadowPaint);
                    graphics.fill(new RoundRectangle2D.Float(SHADOW, SHADOW, TOTAL_W - SHADOW * 2, TOTAL_H - SHADOW * 2, RADIUS, RADIUS));
                    // 窗口背景
                    graphics.setColor(windowBg);
                    graphics.fill(new RoundRectangle2D.Float(SHADOW, SHADOW, TOTAL_W - SHADOW * 2, TOTAL_H - SHADOW * 2, RADIUS, RADIUS));
                    // 标题栏
                    final int pad = 8;
                    final int iconSize = 16;
                    // 图标
                    graphics.setColor(foreground);
                    graphics.fillOval(SHADOW + pad, SHADOW + (HEADER - iconSize) / 2, iconSize, iconSize);
                    // 文件名
                    graphics.setFont(graphics.getFont().deriveFont(13f));
                    graphics.drawString(
                            Opt.ofNullable(item.getVirtualFile()).map(VirtualFile::getName).orElse(""),
                            SHADOW + pad + iconSize + pad, SHADOW + HEADER / 2 + graphics.getFontMetrics().getAscent() / 2 - 2
                    );
                    // 三个控制点
                    final int dot = 4, gap = 6;
                    final int x0 = TOTAL_W - SHADOW - pad - dot * 3 - gap * 2;
                    final int y0 = SHADOW + HEADER / 2;
                    graphics.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 100));
                    graphics.fillOval(x0, y0 - dot / 2, dot, dot);
                    graphics.fillOval(x0 + dot + gap, y0 - dot / 2, dot, dot);
                    graphics.fillOval(x0 + dot * 2 + gap * 2, y0 - dot / 2, dot, dot);
                    // 内容区
                    final Graphics2D graphics2 = (Graphics2D) graphics.create(SHADOW, SHADOW + HEADER, CONTENT_W, CONTENT_H);
                    // 绘制内容
                    component.paint(graphics2);
                    // 销毁内容组件
                    graphics2.dispose();
                    window.dispose();
                    // 1 px 描边
                    graphics.setColor(border);
                    graphics.setStroke(new BasicStroke(1));
                    graphics.draw(new RoundRectangle2D.Float(
                            SHADOW + 0.5f, SHADOW + 0.5f,
                            TOTAL_W - SHADOW * 2 - 1,
                            TOTAL_H - SHADOW * 2 - 1,
                            RADIUS, RADIUS
                    ));
                    // 销毁窗口组件
                    graphics.dispose();
                    // 还原选区
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
     * @param image   图像
     * @param project 项目
     */
    public static void showClipboardFailedNotification(final BufferedImage image, final Project project) {
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
    public static void showCopySuccessNotification(final BufferedImage image, final Project project) {
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
}