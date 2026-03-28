package com.acme.json.helper.core.screenshot;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.core.notice.Notifier;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 代码截图供应商
 * 提供代码截图功能，支持主题适配和文件保存
 *
 * @author 拒绝者
 * @date 2025-11-06
 */
public class CodeScreenshotSupplier {
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 单次 EDT 渲染的最大行数，避免超大选区一次性阻塞 UI
     */
    private static final int MAX_RENDER_LINES_PER_CHUNK = 120;
    /**
     * 图片内边距配置
     */
    private static final Insets IMAGE_PADDING = new Insets(16, 16, 16, 16);

    /**
     * 创建图像
     *
     * @param editor 编辑
     * @return {@link BufferedImage }
     */
    @SuppressWarnings("unused")
    public static BufferedImage createImage(final Editor editor) {
        return createImage(editor, null);
    }

    /**
     * 创建图像
     *
     * @param editor    编辑器
     * @param indicator 进度指示器
     * @return {@link BufferedImage }
     */
    public static BufferedImage createImage(final Editor editor, @Nullable final ProgressIndicator indicator) {
        if (Objects.isNull(editor)) {
            return null;
        }
        final CaptureRange captureRange = runOnEdt(() -> prepareCapture(editor));
        if (Objects.isNull(captureRange)) {
            return null;
        }

        final List<BufferedImage> chunks = new ArrayList<>(captureRange.chunkCount());
        int maxWidth = 0;
        int totalHeight = 0;
        try {
            if (Objects.nonNull(indicator)) {
                indicator.setIndeterminate(Boolean.FALSE);
            }
            for (int chunkIndex = 0; chunkIndex < captureRange.chunkCount(); chunkIndex++) {
                if (Objects.nonNull(indicator)) {
                    indicator.checkCanceled();
                    indicator.setFraction(chunkIndex / (double) captureRange.chunkCount());
                }
                final int fromLine = captureRange.startLine() + chunkIndex * MAX_RENDER_LINES_PER_CHUNK;
                final int toLine = Math.min(captureRange.endLine(), fromLine + MAX_RENDER_LINES_PER_CHUNK - 1);
                final BufferedImage chunkImage = runOnEdt(() -> renderChunk(editor, fromLine, toLine));
                if (Objects.isNull(chunkImage)) {
                    return null;
                }
                chunks.add(chunkImage);
                maxWidth = Math.max(maxWidth, chunkImage.getWidth());
                totalHeight += chunkImage.getHeight();
            }
            if (Objects.nonNull(indicator)) {
                indicator.setFraction(1.0d);
            }
        } finally {
            runOnEdt(() -> {
                restoreSelection(editor, captureRange);
                return null;
            });
        }
        if (chunks.isEmpty()) {
            return null;
        }
        final BufferedImage mergedImage = new BufferedImage(maxWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = mergedImage.createGraphics();
        try {
            int currentY = 0;
            for (final BufferedImage chunk : chunks) {
                graphics.drawImage(chunk, 0, currentY, null);
                currentY += chunk.getHeight();
            }
        } finally {
            graphics.dispose();
        }
        return mergedImage;
    }

    /**
     * 尝试复制图像到剪贴板
     * 将BufferedImage对象复制到系统剪贴板
     *
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
     *
     * @param image   图像
     * @param project 项目
     */
    public static void showClipboardFailedNotification(final BufferedImage image, final Project project) {
        Notifier.notifyWarn(
                BUNDLE.getString("show.clipboard.failed.notification"), project,
                NotificationAction.create(BUNDLE.getString("show.clipboard.notification.action"), (_, _) -> saveToFile(image, project))
        );
    }

    /**
     * 剪贴板成功时提示用户保存文件
     *
     * @param image   图像
     * @param project 项目
     */
    public static void showCopySuccessNotification(final BufferedImage image, final Project project) {
        Notifier.notifyInfo(
                BUNDLE.getString("show.copy.success.notification"), project,
                NotificationAction.create(BUNDLE.getString("show.clipboard.notification.action"), (_, _) -> saveToFile(image, project))
        );
    }

    /**
     * 保存图像到文件
     *
     * @param image   图像
     * @param project 项目
     */
    private static void saveToFile(final BufferedImage image, final Project project) {
        Opt.ofNullable(
                        FileChooserFactory.getInstance().createSaveFileDialog(
                                        new FileSaverDescriptor(BUNDLE.getString("show.clipboard.file.saver.descriptor"), "", "png"), project
                                ).save(buildDefaultFileName())
                ).map(wrapper -> Objects.requireNonNull(wrapper).getFile())
                .ifPresent(file -> CompletableFuture.runAsync(() -> {
                    try (final FileOutputStream fos = new FileOutputStream(file)) {
                        ImageIO.write(image, "png", fos);
                    } catch (final Exception t) {
                        Notifier.notifyError("Failed to write file: %s".formatted(t.getClass().getSimpleName()), project);
                    }
                }, AppExecutorUtil.getAppExecutorService()));
    }

    /**
     * 生成默认文件名
     *
     * @return 默认文件名字符串
     */
    private static String buildDefaultFileName() {
        return StrUtil.format("screenshot-{}.png", DatePattern.PURE_DATETIME_FORMATTER.format(LocalDateTime.now()));
    }

    /**
     * 准备捕获范围以进行后续处理
     * <p> 根据编辑器中的选区信息计算并构建捕获范围对象, 若当前无选区则返回 null
     * <p> 该方法会移除当前的选区, 并基于选区的起始与结束位置计算对应的行号,
     * 同时根据最大渲染行数来确定分块数量
     *
     * @param editor 编辑器实例, 用于获取选区信息及转换偏移量到逻辑位置
     * @return 捕获范围对象, 如果未选中任何内容则返回 null
     */
    private static CaptureRange prepareCapture(final Editor editor) {
        final SelectionModel selection = editor.getSelectionModel();
        if (!selection.hasSelection()) {
            return null;
        }
        final int selectionStart = selection.getSelectionStart();
        final int selectionEnd = selection.getSelectionEnd();
        final int startLine = editor.offsetToLogicalPosition(selectionStart).line;
        final int endLine = editor.offsetToLogicalPosition(selectionEnd).line;
        selection.removeSelection();
        final int lineCount = endLine - startLine + 1;
        final int chunkCount = Math.max(1, (lineCount + MAX_RENDER_LINES_PER_CHUNK - 1) / MAX_RENDER_LINES_PER_CHUNK);
        return new CaptureRange(selectionStart, selectionEnd, startLine, endLine, chunkCount);
    }

    /**
     * 恢复编辑器中的选中范围
     * <p> 根据指定的捕获范围设置编辑器的选中区域
     *
     * @param editor       编辑器实例, 不能为 null
     * @param captureRange 捕获范围, 包含选中开始和结束位置, 不能为 null
     */
    private static void restoreSelection(final Editor editor, final CaptureRange captureRange) {
        editor.getSelectionModel().setSelection(captureRange.selectionStart(), captureRange.selectionEnd());
    }

    /**
     * 渲染编辑器指定行范围的内容为图像
     * <p> 该方法将编辑器中从 fromLine 到 toLine 的内容渲染成一个 BufferedImage 对象,
     * 用于生成可视化图像表示. 此方法会创建一个临时的组件来渲染内容, 并最终返回对应的图像.
     *
     * @param editor   编辑器实例, 不能为 null
     * @param fromLine 起始行号, 包含该行
     * @param toLine   结束行号, 包含该行
     * @return 渲染后的图像对象, 包含指定行范围的内容
     */
    private static BufferedImage renderChunk(final Editor editor, final int fromLine, final int toLine) {
        final int componentEndLine = toLine + 1;
        final EditorFragmentComponent component = EditorFragmentComponent.createEditorFragmentComponent(
                editor, fromLine, componentEndLine, Boolean.TRUE, Boolean.FALSE
        );
        component.setBorder(BorderFactory.createEmptyBorder(
                IMAGE_PADDING.top, IMAGE_PADDING.left, IMAGE_PADDING.bottom, IMAGE_PADDING.right
        ));
        JWindow window = null;
        try {
            window = new JWindow();
            window.getContentPane().add(component);
            window.pack();
            window.setLocation(-9999, -9999);
            window.setSize(component.getPreferredSize());
            window.setVisible(Boolean.TRUE);
            final BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
            final Graphics2D graphics = image.createGraphics();
            try {
                component.paint(graphics);
            } finally {
                graphics.dispose();
            }
            return image;
        } finally {
            if (Objects.nonNull(window)) {
                window.dispose();
            }
        }
    }

    /**
     * 在事件调度线程 (EDT) 中执行指定的操作并返回结果
     * <p>如果当前线程已经是事件调度线程, 则直接执行供应商函数并返回结果;
     * 否则, 将操作在事件调度线程中执行, 并等待其完成.
     * <p>如果在执行过程中发生运行时异常或错误, 将会重新抛出.
     *
     * @param <T>      返回结果的类型
     * @param supplier 提供结果的供应商函数, 不能为 null
     * @return 执行结果
     */
    private static <T> T runOnEdt(final Supplier<T> supplier) {
        if (SwingUtilities.isEventDispatchThread()) {
            return supplier.get();
        }
        final AtomicReference<T> resultRef = new AtomicReference<>();
        final AtomicReference<RuntimeException> runtimeExceptionRef = new AtomicReference<>();
        final AtomicReference<Error> errorRef = new AtomicReference<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                resultRef.set(supplier.get());
            } catch (final RuntimeException e) {
                runtimeExceptionRef.set(e);
            } catch (final Error e) {
                errorRef.set(e);
            }
        });
        if (Objects.nonNull(runtimeExceptionRef.get())) {
            throw runtimeExceptionRef.get();
        }
        if (Objects.nonNull(errorRef.get())) {
            throw errorRef.get();
        }
        return resultRef.get();
    }

    /**
     * 捕获范围记录类
     * <p> 用于表示文本编辑中选区的起始与结束位置, 以及对应的行号和块数量信息
     * <p> 该记录类主要用于编辑器或 IDE 中的文本选择区域管理, 便于进行精确的文本操作和高亮显示
     *
     * @author 拒绝者
     * @date 2026.03.28
     */
    private record CaptureRange(int selectionStart, int selectionEnd, int startLine, int endLine, int chunkCount) {
    }
}
