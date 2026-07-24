package com.acme.prism.core.minimap;

import com.acme.prism.core.settings.ProjectDisposableService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 代码地图（minimap）渲染核心：将编辑器文档渲染为缩略图。
 * <p>每逻辑行一条 2px 高的色带，非空白字符按 {@link CharacterWeights} 油墨权重
 * 以上/下半区透明度写入，远看呈现字符骨架纹理；折叠区域（除首行外）不占缩略高度。</p>
 * <p>线程模型：文档/折叠事件经 150ms 池化线程 Alarm 防抖 → EDT 抓折叠快照与修改戳 →
 * 非阻塞 ReadAction（smart mode）后台遍历 HighlighterIterator 算行数据 →
 * EDT 建/更新 BufferedImage 并回调面板重绘。增量编辑只重扫受影响行、
 * 只在原图上重绘脏 y 带，不新建图片。</p>
 * <p>生命周期：自身注册为 {@link ProjectDisposableService} 的子节点（项目关闭兜底），
 * 编辑器释放时由宿主面板级联 dispose；内部 Alarm 与监听器均托管给自身。</p>
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
public final class MinimapRenderer implements MinimapView, DocumentListener, Disposable {
    /**
     * 缩略图内容列数上限（逻辑像素；行数据按此列数存储）
     */
    private static final int PANEL_WIDTH = 110;
    /**
     * 每逻辑行的缩略像素高（2px：用户验证的高清基线）
     */
    private static final int PIXELS_PER_LINE = 2;
    /**
     * TAB 按 4 列推进
     */
    private static final int TAB_WIDTH = 4;
    /**
     * 防抖延迟（毫秒）：连续输入合并为一次渲染
     */
    private static final int DEBOUNCE_MILLIS = 150;
    /**
     * 渲染行数上限：超过则放弃渲染（image 为 null，documentHeight 为 0）
     */
    private static final int MAX_LINES = 20_000;
    /**
     * 渲染文本长度上限（2MB）：超过则放弃渲染
     */
    private static final int MAX_TEXT_LENGTH = 2 * 1024 * 1024;
    /**
     * 透明色：Src 复合下用于整带清空脏像素
     */
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final Editor editor;
    private final Project project;
    private final Document document;
    private final Runnable repaintCallback;
    private final Alarm refreshAlarm;
    /**
     * 行数据缓存：每逻辑行一个 int[220]（上行 110 + 下行 110，ARGB，0=透明）；全量渲染整体替换引用，增量渲染按行替换元素
     */
    private final List<int[]> lineData = new ArrayList<>();
    /**
     * 待渲染请求状态（EDT 事件写入、池化线程消费，需加锁）
     */
    private final Object pendingLock = new Object();
    private final FoldingListener foldingListener = new FoldingListener() {
        @Override
        public void onFoldRegionStateChange(@NotNull final FoldRegion region) {
            onFoldingChanged();
        }

        @Override
        public void onFoldProcessingEnd() {
            onFoldingChanged();
        }
    };
       private volatile BufferedImage image;
    private volatile List<int[]> foldIntervals = List.of();
    private volatile int documentHeight;
    private volatile boolean disposed;
    /**
     * 上次渲染完成时的文档行数；-1 表示尚未渲染或已因超限放弃
     */
    private int renderedLineCount = -1;
    private boolean pendingFull = true;
    private int pendingStartLine = -1;
    private int pendingEndLine = -1;
    /**
     * 编辑器默认前景色（EDT 上每次渲染前刷新，供后台扫描使用）
     */
    private Color defaultForeground = Color.WHITE;

    /**
     * 构造渲染器并启动首次全量渲染。
     *
     * @param editor          编辑器实例
     * @param project         所属项目
     * @param repaintCallback 渲染完成后的重绘回调（面板注册 repaint）
     */
    public MinimapRenderer(@NotNull final Editor editor, @NotNull final Project project,
                           @NotNull final Runnable repaintCallback) {
        this.editor = editor;
        this.project = project;
        this.document = editor.getDocument();
        this.repaintCallback = repaintCallback;
        // 池化线程 Alarm：防抖调度不占 EDT，托管给自身（dispose 时自动取消）
        this.refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
        // 生命周期由 dispose 显式管理，不挂 parent disposable 避免双重移除（与彩虹高亮 0.17.1/0.17.2 同款约束）
        document.addDocumentListener(this);
        // 折叠变化 → 行高映射失效，必须全量重渲（2026.2 平台折叠监听经 FoldingModelEx 注册）
        if (editor.getFoldingModel() instanceof FoldingModelEx foldingModelEx) {
            foldingModelEx.addListener(foldingListener, this);
        }
        // 项目关闭兜底释放；编辑器释放由宿主面板级联 dispose（见类级 Javadoc）
        Disposer.register(ProjectDisposableService.getInstance(project), this);
        scheduleRefresh();
    }

    @Override
    public void documentChanged(@NotNull final DocumentEvent event) {
        if (disposed || releaseHeavyResourcesIfEditorDisposed()) {
            return;
        }
        synchronized (pendingLock) {
            if (pendingFull) {
                // 已有全量任务排队，全量覆盖一切，无需记录增量范围
            } else if (document.getLineCount() != renderedLineCount) {
                // 总行数变化：行数据缓存尺寸失效，升级为全量
                requestFullRenderLocked();
            } else {
                // 行数不变：合并受影响逻辑行范围，走增量
                final var startLine = document.getLineNumber(event.getOffset());
                final var endOffset = Math.min(event.getOffset() + event.getNewLength(), document.getTextLength());
                final var endLine = document.getLineNumber(endOffset);
                pendingStartLine = pendingStartLine < 0 ? startLine : Math.min(pendingStartLine, startLine);
                pendingEndLine = pendingEndLine < 0 ? endLine : Math.max(pendingEndLine, endLine);
            }
        }
        scheduleRefresh();
    }

    @Override
    public void dispose() {
        if (disposed) {
            // 项目关闭注册与宿主面板级联两条释放路径，防重入
            return;
        }
        disposed = true;
        refreshAlarm.cancelAllRequests();
        document.removeDocumentListener(this);
        lineData.clear();
        image = null;
    }

    @Override
    @Nullable
    public BufferedImage image() {
        return image;
    }

    @Override
    public int documentHeight() {
        return documentHeight;
    }

    @Override
    public int pixelsPerLine() {
        return PIXELS_PER_LINE;
    }

    @Override
    public int yToLogicalLine(final int y) {
        return MinimapGeometry.yToLogicalLine(y, foldIntervals, document.getLineCount(), PIXELS_PER_LINE);
    }

    @Override
    public int logicalLineToY(final int line) {
        return MinimapGeometry.logicalLineToY(line, foldIntervals, PIXELS_PER_LINE);
    }

    /**
     * 将一行数据（上行 110 + 下行 110 列）整段铺入像素缓冲（油墨权重分行）。
     */
    private void copyRowToPixels(final int @NotNull [] row, final int y, final int @NotNull [] pixels) {
        System.arraycopy(row, 0, pixels, y * PANEL_WIDTH, PANEL_WIDTH * PIXELS_PER_LINE);
    }

    /**
     * 折叠状态变化：行高映射失效，调度全量重渲。
     */
    private void onFoldingChanged() {
        if (disposed || releaseHeavyResourcesIfEditorDisposed()) {
            return;
        }
        synchronized (pendingLock) {
            requestFullRenderLocked();
        }
        scheduleRefresh();
    }

    /**
     * 编辑器已释放时被动释放重资源（行数据缓存与图片，单行 110×4B 与 110×3×4B 每逻辑行），
     * 避免宿主面板级联 dispose 之前、共享文档场景下内存驻留。
     *
     * @return true 表示编辑器已释放，调用方应直接返回
     */
    private boolean releaseHeavyResourcesIfEditorDisposed() {
        if (!editor.isDisposed()) {
            return false;
        }
        lineData.clear();
        image = null;
        documentHeight = 0;
        return true;
    }

    /**
     * 在锁内将待渲染请求升级为全量（调用方须持有 {@link #pendingLock}）。
     */
    private void requestFullRenderLocked() {
        pendingFull = true;
        pendingStartLine = -1;
        pendingEndLine = -1;
    }

    /**
     * 调度一次渲染，连续事件经 Alarm 防抖合并。
     */
    private void scheduleRefresh() {
        if (disposed || editor.isDisposed()) {
            return;
        }
        refreshAlarm.cancelAllRequests();
        refreshAlarm.addRequest(this::refresh, DEBOUNCE_MILLIS);
    }

    /**
     * 池化线程：消费待渲染请求，随后切 EDT 做快照与任务分发。
     */
    private void refresh() {
        if (disposed || editor.isDisposed()) {
            return;
        }
        final boolean full;
        final int startLine;
        final int endLine;
        synchronized (pendingLock) {
            full = pendingFull;
            startLine = pendingStartLine;
            endLine = pendingEndLine;
            // 消费后复位：后续事件重新累积
            pendingFull = false;
            pendingStartLine = -1;
            pendingEndLine = -1;
        }
        // FoldingModel 仅限 EDT 访问：先切 EDT 抓折叠快照，再发起非阻塞 ReadAction
        ApplicationManager.getApplication().invokeLater(() -> startRender(full, startLine, endLine));
    }

    /**
     * EDT：防护检查、抓折叠快照与修改戳，按全量/增量分发后台计算。
     */
    private void startRender(final boolean full, final int startLine, final int endLine) {
        if (disposed || editor.isDisposed()) {
            return;
        }
        // 防护：超限文档不渲染
        if (document.getLineCount() > MAX_LINES || document.getTextLength() > MAX_TEXT_LENGTH) {
            applyEmpty();
            return;
        }
        this.defaultForeground = editor.getColorsScheme().getDefaultForeground();
        final var folds = snapshotFoldIntervals();
        final var stamp = document.getModificationStamp();
        if (full || Objects.isNull(image) || renderedLineCount != document.getLineCount()
                || startLine < 0 || endLine < startLine) {
            ReadAction.nonBlocking(() -> computeAllRows(folds))
                    .inSmartMode(project)
                    .expireWith(this)
                    .coalesceBy(MinimapRenderer.this)
                    .finishOnUiThread(ModalityState.any(), result -> applyFull(result, folds, stamp))
                    .submit(AppExecutorUtil.getAppExecutorService());
        } else {
            ReadAction.nonBlocking(() -> computeRowRange(startLine, endLine))
                    .inSmartMode(project)
                    .expireWith(this)
                    .coalesceBy(MinimapRenderer.this)
                    .finishOnUiThread(ModalityState.any(), rows -> applyIncremental(startLine, rows, folds, stamp))
                    .submit(AppExecutorUtil.getAppExecutorService());
        }
    }

    /**
     * EDT：抓取当前折叠区间快照（仅未展开区域），并规范化为合并区间。
     */
    @NotNull
    private List<int[]> snapshotFoldIntervals() {
        final var regions = editor.getFoldingModel().getAllFoldRegions();
        final var intervals = new ArrayList<int[]>(regions.length);
        for (final var region : regions) {
            if (Objects.nonNull(region) && !region.isExpanded()) {
                intervals.add(new int[]{document.getLineNumber(region.getStartOffset()),
                        document.getLineNumber(region.getEndOffset())});
            }
        }
        return MinimapGeometry.mergeFoldIntervals(intervals);
    }

    /**
     * 后台（非阻塞 ReadAction 内）：全量扫描所有逻辑行，并按折叠补偿铺好整图像素。
     */
    @NotNull
    private RenderResult computeAllRows(@NotNull final List<int[]> folds) {
        final var lineCount = document.getLineCount();
        // 空文档（新建空文件/编辑器初始化瞬态）直接返回空结果，避免 getLineEndOffset(-1) 越界
        if (lineCount <= 0) {
            return new RenderResult(List.of(), new int[0], 0);
        }
        final var rows = new ArrayList<int[]>(lineCount);
        scanRows(document.getImmutableCharSequence(), 0, lineCount - 1, rows);
        // 像素缓冲在后台铺好：EDT 只需一次 setRGB 整图写入，避免逐点绘制开销
        final var height = Math.max(MinimapGeometry.documentHeight(lineCount, folds, PIXELS_PER_LINE), PIXELS_PER_LINE);
        final var pixels = new int[PANEL_WIDTH * height];
        for (var line = 0; line < rows.size(); line++) {
            if (MinimapGeometry.isHidden(line, folds)) {
                continue;
            }
            // 行数据自带上下两行像素（油墨权重分行），整段铺入
            copyRowToPixels(rows.get(line), MinimapGeometry.logicalLineToY(line, folds, PIXELS_PER_LINE), pixels);
        }
        return new RenderResult(rows, pixels, height);
    }

    /**
     * 后台（非阻塞 ReadAction 内）：仅重扫 [startLine, endLine] 的逻辑行。
     */
    @NotNull
    private List<int[]> computeRowRange(final int startLine, final int endLine) {
        final var rows = new ArrayList<int[]>(endLine - startLine + 1);
        scanRows(document.getImmutableCharSequence(), startLine, endLine, rows);
        return rows;
    }

    /**
     * 扫描 [startLine, endLine] 的逻辑行，逐 token 逐字符生成行数据（每逻辑行 int[220]：上行 110 + 下行 110，ARGB，0=透明）。
     * <p>采用字形油墨近似算法：每字符按 {@link CharacterWeights} 的上/下半区油墨权重
     * 分别以 [top×0.5, bottom] 的透明度写入上下两行像素，远看呈现字符骨架纹理；
     * 空白仅占列不绘制，TAB 按 4 列推进，超 110 列截断。</p>
     */
    private void scanRows(@NotNull final CharSequence text, final int startLine, final int endLine,
                          @NotNull final List<int[]> rows) {
        final var rangeStart = document.getLineStartOffset(startLine);
        final var rangeEnd = document.getLineEndOffset(endLine);
        final var iterator = ((EditorEx) editor).getHighlighter().createIterator(rangeStart);
        var col = 0;
        var row = new int[PANEL_WIDTH * PIXELS_PER_LINE];
        while (!iterator.atEnd() && iterator.getStart() < rangeEnd) {
            // 每 token 预算 RGB 部分，字符级仅做 alpha 拼接（位移运算）
            final var rgbPart = foregroundOf(iterator).getRGB() & 0x00FFFFFF;
            // token 可能跨行：裁剪到目标行范围，列号从行首起算
            final var tokenStart = Math.max(iterator.getStart(), rangeStart);
            final var tokenEnd = Math.min(iterator.getEnd(), rangeEnd);
            for (var offset = tokenStart; offset < tokenEnd; offset++) {
                final var c = text.charAt(offset);
                if (c == '\n') {
                    rows.add(row);
                    row = new int[PANEL_WIDTH * PIXELS_PER_LINE];
                    col = 0;
                    continue;
                }
                if (c == '\t') {
                    col += TAB_WIDTH;
                    continue;
                }
                if (!Character.isWhitespace(c) && col < PANEL_WIDTH) {
                    final var ascii = c < 128;
                    final var topWeight = ascii ? CharacterWeights.TOP_WEIGHT[c] : CharacterWeights.DEFAULT_WEIGHT;
                    final var bottomWeight = ascii ? CharacterWeights.BOTTOM_WEIGHT[c] : CharacterWeights.DEFAULT_WEIGHT;
                    row[col] = (int) (topWeight * 0.5f * 0xFF) << 24 | rgbPart;
                    row[PANEL_WIDTH + col] = (int) (bottomWeight * 0xFF) << 24 | rgbPart;
                }
                col++;
            }
            iterator.advance();
        }
        rows.add(row);
    }

    /**
     * 取 token 前景色：语法属性缺失时回退编辑器默认前景色。
     */
    @NotNull
    private Color foregroundOf(@NotNull final HighlighterIterator iterator) {
        final var attributes = iterator.getTextAttributes();
        final var color = Objects.isNull(attributes) ? null : attributes.getForegroundColor();
        return Objects.nonNull(color) ? color : defaultForeground;
    }

    /**
     * EDT：应用全量结果——替换行数据缓存，必要时重建图片，一次 setRGB 整图写入。
     */
    private void applyFull(@NotNull final RenderResult result, @NotNull final List<int[]> folds, final long stamp) {
        if (disposed || editor.isDisposed()) {
            return;
        }
        // 空结果（空文档）：走清空路径，避免创建零高图片
        if (result.height() <= 0) {
            applyEmpty();
            return;
        }
        // 计算期间文档又发生变化：结果已过期，丢弃并重新全量
        if (document.getModificationStamp() != stamp || result.rows().size() != document.getLineCount()) {
            synchronized (pendingLock) {
                requestFullRenderLocked();
            }
            scheduleRefresh();
            return;
        }
        lineData.clear();
        lineData.addAll(result.rows());
        foldIntervals = folds;
        renderedLineCount = result.rows().size();
        documentHeight = MinimapGeometry.documentHeight(renderedLineCount, folds, PIXELS_PER_LINE);
        var img = image;
        if (Objects.isNull(img) || img.getWidth() != PANEL_WIDTH || img.getHeight() != result.height()) {
            img = new BufferedImage(PANEL_WIDTH, result.height(), BufferedImage.TYPE_INT_ARGB);
            image = img;
        }
        img.setRGB(0, 0, PANEL_WIDTH, result.height(), result.pixels(), 0, PANEL_WIDTH);
        repaintCallback.run();
    }

    /**
     * EDT：应用增量结果——行数据按行替换，原图上仅用 Graphics2D 重绘受影响可见行的脏 y 带（不新建图片）。
     */
    private void applyIncremental(final int startLine, @NotNull final List<int[]> rows,
                                  @NotNull final List<int[]> folds, final long stamp) {
        if (disposed || editor.isDisposed()) {
            return;
        }
        final var img = image;
        // 状态漂移（计算期间文档又变更/尚未全量/行数对不上）：回退全量
        if (document.getModificationStamp() != stamp || Objects.isNull(img)
                || renderedLineCount != document.getLineCount()
                || startLine < 0 || startLine + rows.size() > lineData.size()) {
            synchronized (pendingLock) {
                requestFullRenderLocked();
            }
            scheduleRefresh();
            return;
        }
        for (var i = 0; i < rows.size(); i++) {
            lineData.set(startLine + i, rows.get(i));
        }
        foldIntervals = folds;
        final var graphics = img.createGraphics();
        try {
            // Src 复合：源色（含透明）直接覆盖目标像素，先整带写透明清掉旧点再画新点
            graphics.setComposite(AlphaComposite.Src);
            for (var line = startLine; line < startLine + rows.size(); line++) {
                if (MinimapGeometry.isHidden(line, folds)) {
                    continue;
                }
                final var y = MinimapGeometry.logicalLineToY(line, folds, PIXELS_PER_LINE);
                graphics.setColor(TRANSPARENT);
                graphics.fillRect(0, y, PANEL_WIDTH, PIXELS_PER_LINE);
                final var row = lineData.get(line);
                // 按上下半区逐像素写点（行数据上行 110 + 下行 110）
                for (var dy = 0; dy < PIXELS_PER_LINE; dy++) {
                    for (var x = 0; x < PANEL_WIDTH; x++) {
                        final var argb = row[dy * PANEL_WIDTH + x];
                        if (argb != 0) {
                            graphics.setColor(new Color(argb, Boolean.TRUE));
                            graphics.fillRect(x, y + dy, 1, 1);
                        }
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
        repaintCallback.run();
    }

    /**
     * EDT：超限文档放弃渲染——清空缓存与图片，高度归零。
     */
    private void applyEmpty() {
        lineData.clear();
        foldIntervals = List.of();
        renderedLineCount = -1;
        documentHeight = 0;
        image = null;
        repaintCallback.run();
    }

    /**
     * 全量渲染结果：行数据 + 已按折叠补偿铺好的整图像素缓冲。
     *
     * @param rows   全部逻辑行的行数据
     * @param pixels 整图 ARGB 像素缓冲（宽 110）
     * @param height 整图高度（像素）
     */
    private record RenderResult(@NotNull List<int[]> rows, int @NotNull [] pixels, int height) {
    }
}
