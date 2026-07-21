package com.acme.json.helper.core.rainbow;

import com.acme.json.helper.core.settings.PluginSettingsState;
import com.acme.json.helper.core.settings.ProjectDisposableService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.ColorPicker;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 颜色字面量高亮监听器（每编辑器一个）。
 * <p>监听文档变化，经 300ms 防抖后在 Alarm 池化线程中执行正则扫描，
 * 再回到 EDT 刷新 Gutter 色块图标；EDT 上只做 MarkupModel 写操作，避免卡顿。</p>
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
final class ColorHighlightListener implements DocumentListener {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 防抖延迟（毫秒）：连续输入时合并为一次扫描
     */
    private static final int REFRESH_DELAY_MILLIS = 300;
    /**
     * 大文件跳过阈值（512KB）：超过该体积不做颜色扫描，防止正则全文扫描拖累 IDE
     */
    private static final int MAX_SCAN_BYTES = 512 * 1024;
    /**
     * 单次扫描命中数上限：防止极端文本产生过多 Gutter 图标与 RangeHighlighter
     */
    private static final int MAX_MATCHES = 500;
    /**
     * 高亮层：色块高亮不含文本属性，取最低层避免干扰文本着色。
     * <p>注意：2026.2 平台已移除 HighlighterLayer.LINE_MARKERS 常量，此处改用 SYNTAX 层。</p>
     */
    private static final int HIGHLIGHT_LAYER = HighlighterLayer.SYNTAX;

    private final Editor editor;
    private final Document document;
    private final Project project;
    private final ProjectDisposableService disposable;
    private final Alarm refreshAlarm;
    private final List<RangeHighlighter> activeHighlighters = new ArrayList<>();
    private volatile boolean disposed;

    /**
     * 构造监听器并注册到编辑器。
     *
     * @param editor  编辑器实例
     * @param project 所属项目
     */
    ColorHighlightListener(@NotNull final Editor editor, @NotNull final Project project) {
        this.editor = editor;
        this.document = editor.getDocument();
        this.project = project;
        this.disposable = ProjectDisposableService.getInstance(project);
        // 池化线程 Alarm：扫描在后台线程执行，不占用 EDT
        this.refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable);
        // 生命周期由 EditorFactoryListener.editorReleased 显式管理，不挂 parent disposable 避免双重移除
        this.document.addDocumentListener(this);
        scheduleRefresh();
    }

    @Override
    public void documentChanged(@NotNull final DocumentEvent event) {
        scheduleRefresh();
    }

    /**
     * 清理监听器、取消防抖任务并移除全部高亮。
     */
    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        refreshAlarm.cancelAllRequests();
        document.removeDocumentListener(this);
        clearHighlighters();
    }

    /**
     * 调度一次刷新，连续事件会被合并。
     */
    private void scheduleRefresh() {
        if (editor.isDisposed()) {
            return;
        }
        refreshAlarm.cancelAllRequests();
        refreshAlarm.addRequest(this::refresh, REFRESH_DELAY_MILLIS);
    }

    /**
     * 后台线程刷新：取不可变文本快照（零拷贝）并扫描，随后切回 EDT 应用结果。
     */
    private void refresh() {
        if (editor.isDisposed()) {
            return;
        }
        if (!isEnabled() || document.getTextLength() > MAX_SCAN_BYTES) {
            // 开关关闭或超大文件：清除已有色块后跳过本轮扫描
            ApplicationManager.getApplication().invokeLater(this::clearHighlighters);
            return;
        }
        // 不可变字符序列是文档的零拷贝快照视图，线程安全；扫描不在读动作内，不阻塞写入
        final var matches = ColorLiteralParser.scan(document.getImmutableCharSequence(), MAX_MATCHES);
        ApplicationManager.getApplication().invokeLater(() -> applyHighlighters(matches));
    }

    /**
     * 将扫描结果应用到编辑器（必须在 EDT 执行）：先清旧高亮，再逐条重建。
     */
    private void applyHighlighters(@NotNull final List<ColorLiteralParser.ColorMatch> matches) {
        if (editor.isDisposed()) {
            return;
        }
        clearHighlighters();
        final var markupModel = editor.getMarkupModel();
        final var maxOffset = document.getTextLength();
        for (final var match : matches) {
            // 扫描到应用之间文本可能再次变化，越界命中直接丢弃，等待下一轮防抖刷新修正
            if (match.start() < 0 || match.end() > maxOffset) {
                continue;
            }
            final var highlighter = markupModel.addRangeHighlighter(
                    match.start(),
                    match.end(),
                    HIGHLIGHT_LAYER,
                    null,
                    HighlighterTargetArea.EXACT_RANGE);
            highlighter.setGutterIconRenderer(new ColorGutterIconRenderer(match.color(), match.start(), match.end()));
            activeHighlighters.add(highlighter);
        }
    }

    /**
     * 弹出调色板修改颜色并回写文档（保持原字面量格式）。
     *
     * @param current 当前颜色（调色板预选）
     * @param start   字面量起始偏移
     * @param end     字面量结束偏移
     */
    private void chooseColor(@NotNull final Color current, final int start, final int end) {
        if (editor.isDisposed() || this.project.isDisposed()) {
            return;
        }
        final Color newColor = ColorPicker.showDialog(editor.getContentComponent(),
                BUNDLE.getString("color.highlighter.choose.color.title"), current, Boolean.TRUE, List.of(), Boolean.TRUE);
        if (Objects.isNull(newColor)) {
            return;
        }
        WriteCommandAction.runWriteCommandAction(this.project, () -> {
            if (editor.isDisposed() || end > document.getTextLength()) {
                return;
            }
            document.replaceString(start, end, formatColor(newColor, document.getText(new TextRange(start, end))));
        });
    }

    /**
     * 按原字面量格式输出新颜色文本。
     * <p>HEX：原带 alpha（#RGBA / #RRGGBBAA 或新色含透明度）输出 8 位，否则 6 位；
     * 函数式：原 rgba 输出 rgba（alpha 两位小数），原 rgb 输出 rgb</p>
     *
     * @param color    新颜色
     * @param original 原字面量文本
     * @return 格式化后的颜色文本
     */
    private static String formatColor(@NotNull final Color color, @NotNull final String original) {
        if (original.startsWith("#")) {
            return original.length() == 5 || original.length() == 9 || color.getAlpha() != 255
                    ? "#%02X%02X%02X%02X".formatted(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                    : "#%02X%02X%02X".formatted(color.getRed(), color.getGreen(), color.getBlue());
        }
        final String prefix = original.startsWith("rgba") || color.getAlpha() != 255 ? "rgba" : "rgb";
        return "rgba".equals(prefix)
                ? "rgba(%d, %d, %d, %s)".formatted(color.getRed(), color.getGreen(), color.getBlue(),
                        String.format(Locale.ROOT, "%.2f", color.getAlpha() / 255.0))
                : "rgb(%d, %d, %d)".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * 清除当前编辑器上的全部颜色高亮（必须在 EDT 执行）。
     */
    private void clearHighlighters() {
        if (!editor.isDisposed()) {
            final var markupModel = editor.getMarkupModel();
            for (final var highlighter : activeHighlighters) {
                markupModel.removeHighlighter(highlighter);
            }
        }
        activeHighlighters.clear();
    }

    /**
     * 判断功能是否开启。
     */
    private boolean isEnabled() {
        return PluginSettingsState.getInstance().colorHighlighterEnabled;
    }

    /**
     * 颜色 Gutter 图标渲染器：equals/hashCode 按颜色值实现，保证同行同色图标可被 Gutter 合并机制去重；
     * 点击图标弹出调色板修改颜色并回写文档
     */
    private final class ColorGutterIconRenderer extends GutterIconRenderer {
        private final ColorIcon icon;
        private final int start;
        private final int end;

        private ColorGutterIconRenderer(@NotNull final Color color, final int start, final int end) {
            this.icon = new ColorIcon(color);
            this.start = start;
            this.end = end;
        }

        @Override
        @NotNull
        public Icon getIcon() {
            return icon;
        }

        @Override
        public @Nullable AnAction getClickAction() {
            return new DumbAwareAction() {
                @Override
                public void actionPerformed(@NotNull final AnActionEvent e) {
                    chooseColor(icon.color, ColorGutterIconRenderer.this.start, ColorGutterIconRenderer.this.end);
                }
            };
        }

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof ColorGutterIconRenderer other && Objects.equals(icon, other.icon);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(icon);
        }
    }

    /**
     * 12x12 纯色方形图标：自绘色块 + 1px 黑色描边，equals/hashCode 按颜色值实现。
     */
    private static final class ColorIcon implements Icon {
        /**
         * 图标边长（像素）
         */
        private static final int SIZE = 12;

        private final Color color;

        private ColorIcon(@NotNull final Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(final Component component, @NotNull final Graphics graphics, final int x, final int y) {
            graphics.setColor(color);
            graphics.fillRect(x, y, SIZE, SIZE);
            graphics.setColor(Color.BLACK);
            graphics.drawRect(x, y, SIZE - 1, SIZE - 1);
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof ColorIcon other && color.getRGB() == other.color.getRGB();
        }

        @Override
        public int hashCode() {
            return color.getRGB();
        }
    }
}
