package com.acme.json.helper.core.minimap;

import com.acme.json.helper.core.settings.PluginSettingsState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 代码缩略图显示面板（每编辑器一个，挂载于编辑器组件右侧）。
 * <p>以编辑器背景色显式铺底（滚动容器默认底色发白，不能依赖透明透出），
 * 仅绘制缩略图、错误条纹、Git 行状态条纹与视口框；左缘拖拽可在 40~200 像素间调节宽度并持久化；右键菜单提供功能开关。</p>
 *
 * @author 拒绝者
 * @date 2026-07-21
 */
public final class MinimapPanel extends JPanel implements Disposable {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 视口框半透明填充色：亮色/暗色主题均 8% 透明度（仅作位置提示；
     * 小文件全文一屏时视口覆盖整个面板，过浓填充会把整块 minimap 糊成灰白色）
     */
    private static final JBColor VIEWPORT_FILL = new JBColor(new Color(0x14000000, true), new Color(0x14FFFFFF, true));
    /**
     * Git 行状态条纹宽度（像素，贴 minimap 左缘的竖条，对应编辑器行号槽左侧的 VCS 标记）
     */
    private static final int VCS_STRIPE_WIDTH = 3;
    /**
     * 缩略图整体绘制透明度（纹理全浓度，2px 行高小点需要足量透明保证清晰可读）
     */
    private static final float IMAGE_ALPHA = 1.0f;
    /**
     * 左缘调宽热区宽度（像素）
     */
    private static final int RESIZE_ZONE_WIDTH = 4;
    /**
     * 面板可调最小宽度（像素）
     */
    private static final int MIN_PANEL_WIDTH = 40;
    /**
     * 面板可调最大宽度（像素）
     */
    private static final int MAX_PANEL_WIDTH = 200;

    private final Editor editor;
    private final MinimapView view;
    /**
     * 可见区滚动监听：编辑器滚动后重算滚动状态并重绘视口框
     */
    private final VisibleAreaListener visibleAreaListener;
    /**
     * 鼠标交互监听（点击定位 / 拖动滚动 / Shift 扩选 / 右键菜单 / 左缘调宽）
     */
    private final MouseAdapter mouseHandler;
    /**
     * 面板尺寸监听：尺寸变化时重绘
     */
    private final ComponentAdapter componentListener;
    /**
     * 上次绘制时缩略图采样源起点（缩略图 y 坐标），即鼠标点击换算所用的当前采样偏移；仅 EDT 读写
     */
    private int sourceStartY;
    /**
     * 上次绘制时的纵向拉伸系数（文档不足面板高时 >1；鼠标点击换算需先除以该系数还原缩略坐标）
     */
    private double effectiveScale = 1.0;
    /**
     * 是否处于导航拖拽中（左键按下进入，松开结束）
     */
    private boolean dragging;
    /**
     * 拖拽抓取点：鼠标按下时相对视口框顶部的面板 y 偏移（拖动全程保持该偏移，
     * 视口框 1:1 跟随鼠标——与原生滚动条滑块手感一致）
     */
    private int dragGrabOffsetY;
    /**
     * 拖拽时锁定的视口映射系数与行程（按下时快照）：拖拽全程灵敏度恒定，
     * 避免软换行区视口框高度随位置变化导致鼠标与视口框相对位置漂移
     */
    private double dragFactor = 1.0;
    /**
     * 拖拽时锁定的视口起点缩略 y 上限（文档缩略高 - 视口缩略高，按下时快照）
     */
    private int dragMaxViewportStart;
    /**
     * 左缘调宽拖动起点 x 坐标（-1 表示未在调宽）
     */
    private int resizeStartX = -1;
    /**
     * 左缘调宽拖动起点时的面板宽度基准
     */
    private int resizeStartWidth;
    private volatile boolean disposed;

    /**
     * 构造缩略图面板并注册全部监听器，生命周期挂到父 Disposable。
     *
     * @param editor           编辑器
     * @param project          所属项目（当前实现未直接使用，随构造约定保留）
     * @param view             缩略图视图（由渲染核心实现）
     * @param parentDisposable 父 Disposable（项目关闭时自动 dispose；编辑器释放时由工厂监听器显式 dispose）
     */
    public MinimapPanel(@NotNull final Editor editor, @NotNull final Project project,
                        @NotNull final MinimapView view, @NotNull final Disposable parentDisposable) {
        this.editor = editor;
        this.view = view;
        // 透明背景：透出编辑器底色，缩略字符直接叠在代码背景上（避免面板底色发白）
        setOpaque(false);
        setPreferredSize(new Dimension(PluginSettingsState.getInstance().minimapWidth, 0));
        // 编辑器滚动 → 直接 repaint（Swing 自动合并连续重绘请求，无需自行节流）
        this.visibleAreaListener = event -> repaint();
        editor.getScrollingModel().addVisibleAreaListener(visibleAreaListener);
        this.mouseHandler = new MinimapMouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
        this.componentListener = new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                repaint();
            }
        };
        addComponentListener(componentListener);
        Disposer.register(parentDisposable, this);
    }

    /**
     * 分层绘制：①缩略图按滚动状态平移采样（图像全宽缩放到面板宽）；②错误/警告线条整行条纹；③视口框。
     * 面板透明（opaque=false），缩略图未就绪且无内容（image 为 null 且 documentHeight 为 0）时不画任何内容。
     */
    @Override
    protected void paintComponent(final Graphics graphics) {
        // 显式以编辑器背景色铺底：面板挂在编辑器滚动容器（JScrollPane）上，
        // 透明时会透出容器的 LaF 默认灰白背景，必须自行填充编辑器底色
        graphics.setColor(editor.getColorsScheme().getDefaultBackground());
        graphics.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(graphics);
        final var state = MinimapScrollState.of(editor, view);
        final var image = view.image();
        if (Objects.isNull(image) && state.documentHeight() == 0) {
            return;
        }
        final var width = getWidth();
        // 实际绘制高度：不超过滚动状态给出的绘制高度与面板当前可视高度
        final var drawHeight = Math.min(state.drawHeight(), getHeight());
        if (width <= 0 || drawHeight <= 0) {
            return;
        }
        // 比例映射：视口在文档中的滚动比例 × 可平移区间 = 采样起点（拖动时视口框随滚动连续移动，跟手；
        // 居中式钳制会让视口框在大部分行程固定不动，产生"鼠标在前滑块在后"的脱节感）
        sourceStartY = sourceStartYOf(state, drawHeight);
        effectiveScale = 1.0;
        if (Objects.nonNull(image)) {
            // 源矩形取图像全宽（110 列），目标按面板宽缩放——宽度调节零渲染成本（Java2D 采样缩放）；
            // 最近邻插值保持像素边界（默认双线性会把相邻字符点混成灰白雾），SRC_OVER 0.8 让纹理再淡一档
            final var sourceEndY = Math.min(sourceStartY + drawHeight, Math.min(state.documentHeight(), image.getHeight()));
            if (sourceEndY > sourceStartY && graphics instanceof Graphics2D graphics2D) {
                final var defaultComposite = graphics2D.getComposite();
                // NEAREST 插值：2:1 整数缩放比下像素级无损，且滚动重绘开销远低于 BICUBIC（滚动跟手）
                graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics2D.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, IMAGE_ALPHA));
                graphics2D.drawImage(image, 0, 0, width, sourceEndY - sourceStartY,
                        0, sourceStartY, image.getWidth(), sourceEndY, null);
                graphics2D.setComposite(defaultComposite);
            }
        }
        if (PluginSettingsState.getInstance().minimapErrorStripeEnabled) {
            paintErrorStripes(graphics, width);
            paintVcsStripes(graphics);
        }
        // 视口框：仅 8% 透明圆角填充（无边框线，几乎透明但可辨识当前可视区域）
        if (state.viewportHeight() > 0) {
            final var viewportY = (int) ((state.viewportStart() - sourceStartY) * effectiveScale);
            graphics.setColor(VIEWPORT_FILL);
            graphics.fillRoundRect(0, viewportY, width, (int) (state.viewportHeight() * effectiveScale), 5, 5);
        }
    }

    /**
     * 视口比例映射的采样起点：视口滚动比例 × 可平移区间（绘制与拖拽抓取共用同一公式，保证正反换算互逆）。
     */
    private static int sourceStartYOf(@NotNull final MinimapScrollState state, final int drawHeight) {
        return state.documentHeight() <= drawHeight ? 0
                : state.viewportStart() <= 0 ? 0
                : (int) Math.min(state.documentHeight() - drawHeight,
                        state.viewportStart() / (double) Math.max(1, state.documentHeight() - state.viewportHeight())
                                * (state.documentHeight() - drawHeight));
    }

    /**
     * 绘制错误/警告/异味行的整行高亮条纹（全色横条，黄/红一眼可辨）。
     * <p>高亮来源：编辑器 MarkupModel 与文档 MarkupModel 双通道合并（部分检查高亮只落在文档侧）；
     * 颜色回退链：显式 error-stripe 色 → 文本属性 errorStripeColor → 波浪线效果色
     * （很多异味检查只配黄/红波浪线，不配 error-stripe 色，不配回退会漏显）。</p>
     */
    private void paintErrorStripes(@NotNull final Graphics graphics, final int width) {
        final var document = editor.getDocument();
        final var project = editor.getProject();
        final var highlighters = new java.util.ArrayList<>(List.of(editor.getMarkupModel().getAllHighlighters()));
        if (Objects.nonNull(project)) {
            final var documentMarkup = com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument(document, project, false);
            if (Objects.nonNull(documentMarkup)) {
                highlighters.addAll(List.of(documentMarkup.getAllHighlighters()));
            }
        }
        final var scheme = editor.getColorsScheme();
        for (final var highlighter : highlighters) {
            var stripeColor = highlighter.getErrorStripeMarkColor(scheme);
            if (Objects.isNull(stripeColor)) {
                final var attributes = highlighter.getTextAttributes(scheme);
                if (Objects.nonNull(attributes)) {
                    stripeColor = Objects.nonNull(attributes.getErrorStripeColor())
                            ? attributes.getErrorStripeColor()
                            : attributes.getEffectColor();
                }
            }
            if (Objects.isNull(stripeColor)) {
                continue;
            }
            final var startLine = document.getLineNumber(Math.min(highlighter.getStartOffset(), document.getTextLength()));
            final var endLine = document.getLineNumber(Math.min(highlighter.getEndOffset(), document.getTextLength()));
            final var y = (int) ((view.logicalLineToY(startLine) - sourceStartY) * effectiveScale);
            final var yEnd = (int) ((view.logicalLineToY(endLine) + view.pixelsPerLine() - sourceStartY) * effectiveScale);
            graphics.setColor(toOpaque(stripeColor));
            graphics.fillRect(0, Math.max(y, 0), width, Math.min(yEnd, getHeight()) - Math.max(y, 0));
        }
    }

    /**
     * 条纹强制不透明：2px 行高的横条必须全色才能在代码纹理上可辨；
     * error-stripe 标记色的 alpha 可能为 0 或半透明（尤其弱警告），直接绘制会看不见
     */
    private static Color toOpaque(@NotNull final Color color) {
        return new Color(color.getRGB());
    }

    /**
     * 绘制 Git 行状态条纹（新增/修改/删除）：贴 minimap 左缘的竖条，对应编辑器行号槽左侧的 VCS 标记。
     * <p>行状态经 {@link LineStatusTrackerManager} 实时读取，编辑/回滚后随 minimap 重绘同步；
     * 颜色取 Color Scheme &gt; VCS 的 Added/Modified/Deleted Lines；文件未纳入版本管理时静默跳过。</p>
     */
    private void paintVcsStripes(@NotNull final Graphics graphics) {
        final var project = editor.getProject();
        if (Objects.isNull(project)) {
            return;
        }
        final var tracker = LineStatusTrackerManager.Companion.getInstance(project).getLineStatusTracker(editor.getDocument());
        if (Objects.isNull(tracker) || !tracker.isValid()) {
            return;
        }
        final var scheme = editor.getColorsScheme();
        for (final var range : tracker.getRanges()) {
            final var color = switch (range.getType()) {
                case Range.INSERTED -> scheme.getColor(EditorColors.ADDED_LINES_COLOR);
                case Range.MODIFIED -> scheme.getColor(EditorColors.MODIFIED_LINES_COLOR);
                case Range.DELETED -> scheme.getColor(EditorColors.DELETED_LINES_COLOR);
                default -> null;
            };
            if (Objects.isNull(color)) {
                continue;
            }
            // Range 行区间为 [line1, line2) 开区间；删除标记无行（line1==line2），画在所在行位置
            final var y = (int) ((view.logicalLineToY(range.getLine1()) - sourceStartY) * effectiveScale);
            final var yEnd = range.hasLines()
                    ? (int) ((view.logicalLineToY(range.getLine2() - 1) + view.pixelsPerLine() - sourceStartY) * effectiveScale)
                    : y + Math.max(1, (int) (view.pixelsPerLine() * effectiveScale));
            graphics.setColor(color);
            graphics.fillRect(0, Math.max(y, 0), VCS_STRIPE_WIDTH, Math.min(yEnd, getHeight()) - Math.max(y, 0));
        }
    }

    /**
     * 弹出右键菜单（ToggleAction 即时读写的功能开关）。
     */
    private void showPopupMenu(@NotNull final MouseEvent e) {
        final var settings = PluginSettingsState.getInstance();
        final var actions = new DefaultActionGroup();
        actions.add(new ToggleAction(BUNDLE.getString("minimap.menu.show")) {
            @Override
            public boolean isSelected(@NotNull final AnActionEvent event) {
                return settings.minimapEnabled;
            }

            @Override
            public void setSelected(@NotNull final AnActionEvent event, final boolean state) {
                settings.minimapEnabled = state;
                MinimapEditorFactoryListener.syncAllEditors();
            }
        });
        actions.add(new ToggleAction(BUNDLE.getString("minimap.menu.error.stripe")) {
            @Override
            public boolean isSelected(@NotNull final AnActionEvent event) {
                return settings.minimapErrorStripeEnabled;
            }

            @Override
            public void setSelected(@NotNull final AnActionEvent event, final boolean state) {
                settings.minimapErrorStripeEnabled = state;
                repaint();
            }
        });
        actions.add(new ToggleAction(BUNDLE.getString("minimap.menu.hide.scrollbar")) {
            @Override
            public boolean isSelected(@NotNull final AnActionEvent event) {
                return settings.minimapHideOriginalScrollBar;
            }

            @Override
            public void setSelected(@NotNull final AnActionEvent event, final boolean state) {
                settings.minimapHideOriginalScrollBar = state;
                MinimapEditorFactoryListener.syncScrollBarVisibility();
            }
        });
        JBPopupFactory.getInstance().createActionGroupPopup(null, actions,
                com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT, false, true, false, null, -1, null)
                .show(new com.intellij.ui.awt.RelativePoint(e));
    }

    /**
     * 释放资源：移除滚动、鼠标与尺寸全部监听器（编辑器已释放时滚动模型随之销毁，无需再移除），
     * 并级联 dispose 渲染核心（{@link MinimapView} 由 MinimapRenderer 实现，其行数据缓存与图片随编辑器释放）。
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (!editor.isDisposed()) {
            editor.getScrollingModel().removeVisibleAreaListener(visibleAreaListener);
        }
        removeMouseListener(mouseHandler);
        removeMouseMotionListener(mouseHandler);
        removeMouseWheelListener(mouseHandler);
        removeComponentListener(componentListener);
        // 级联释放渲染核心：渲染器已自注册到 Disposer 树，此处走树销毁保证其 Alarm 与监听器一并清理
        if (view instanceof Disposable disposable) {
            Disposer.dispose(disposable);
        }
    }

    /**
     * 鼠标交互：左键点击定位光标并居中滚动，拖动按缩略比例滚动编辑器，Shift+点击以当前光标为锚点扩选，
     * 右键弹出功能菜单，左缘拖拽调节面板宽度，滚轮滚动编辑器。
     */
    private final class MinimapMouseHandler extends MouseAdapter {
        @Override
        public void mousePressed(@NotNull final MouseEvent e) {
            if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                showPopupMenu(e);
                return;
            }
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            // 左缘热区：进入调宽拖动（记录基准，不参与导航）
            if (e.getX() <= RESIZE_ZONE_WIDTH) {
                resizeStartX = e.getXOnScreen();
                resizeStartWidth = getWidth();
                setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
                return;
            }
            // 换算链：面板 y ÷ 拉伸系数 + 当前采样偏移 → 缩略图 y → 逻辑行（钳制到文档行数内）→ 行首偏移
            final var document = editor.getDocument();
            final var line = Math.max(0, Math.min(view.yToLogicalLine((int) (e.getY() / effectiveScale) + sourceStartY), document.getLineCount() - 1));
            final var offset = Math.min(document.getLineStartOffset(line), document.getTextLength());
            if (e.isShiftDown()) {
                // Shift+点击：以原光标位置为锚点扩选到目标位置，随后滚动到目标处（与编辑器原生行为一致）
                final var anchor = editor.getCaretModel().getOffset();
                editor.getCaretModel().moveToOffset(offset);
                editor.getSelectionModel().setSelection(Math.min(anchor, offset), Math.max(anchor, offset));
            } else {
                editor.getCaretModel().moveToOffset(offset);
            }
            // 即时滚动居中：scrollVertically 直设滚动偏移（无平滑滚动动画），
            // 避免 scrollToCaret 动画期间 visibleArea 滞后、且动画与后续拖拽争抢滚动位置
            final var visibleHeight = editor.getScrollingModel().getVisibleArea().height;
            editor.getScrollingModel().scrollVertically(editor.offsetToXY(offset).y - visibleHeight / 2);
            // 记录拖拽基准：注意跳转后尚未触发重绘，sourceStartY 字段仍是旧采样偏移，
            // 必须用绘制同款公式从新状态现算对齐，否则抓取偏移混入新旧采样差，
            // 拖拽全程视口框与鼠标恒差一段距离（"总是差一点跟不上"）
            dragging = true;
            final var state = MinimapScrollState.of(editor, view);
            final var drawHeight = Math.min(state.drawHeight(), getHeight());
            dragGrabOffsetY = e.getY() - (int) ((state.viewportStart() - sourceStartYOf(state, drawHeight)) * effectiveScale);
            // 锁定本次拖拽的映射系数与行程：灵敏度全程恒定（软换行区视口框高度随位置变，逐帧重算会漂移）
            dragFactor = state.documentHeight() <= drawHeight ? 1.0
                    : (state.documentHeight() - state.viewportHeight()) / (double) Math.max(1, drawHeight - state.viewportHeight());
            dragMaxViewportStart = Math.max(0, state.documentHeight() - state.viewportHeight());
        }

        @Override
        public void mouseDragged(@NotNull final MouseEvent e) {
            if (resizeStartX >= 0) {
                // 左缘调宽：以屏幕坐标 delta 向左加宽，钳制到可调范围并实时生效
                final var newWidth = Math.max(MIN_PANEL_WIDTH,
                        Math.min(MAX_PANEL_WIDTH, resizeStartWidth - (e.getXOnScreen() - resizeStartX)));
                setPreferredSize(new Dimension(newWidth, 0));
                revalidate();
                return;
            }
            if (!dragging || !SwingUtilities.isLeftMouseButton(e) || dragMaxViewportStart <= 0) {
                return;
            }
            // 视口框跟随鼠标模型（与原生滚动条滑块一致）：目标视口框顶 = 鼠标 y - 抓取偏移；
            // 面板 y → 视口起点缩略 y 乘按下时锁定的放大系数（长文档时视口起点行程大于视口框面板行程）
            final var targetViewportStart = Math.max(0, Math.min(dragMaxViewportStart,
                    (int) ((e.getY() - dragGrabOffsetY) / effectiveScale * dragFactor)));
            // 视口起点缩略 y → 逻辑行 → 视觉像素 y（logicalPositionToXY 内含软换行/折叠换算），与视口框正向映射互逆
            final var targetLine = view.yToLogicalLine(targetViewportStart);
            editor.getScrollingModel().scrollVertically(editor.logicalPositionToXY(new LogicalPosition(targetLine, 0)).y);
        }

        @Override
        public void mouseReleased(@NotNull final MouseEvent e) {
            if (resizeStartX >= 0) {
                // 调宽结束：持久化当前宽度
                PluginSettingsState.getInstance().minimapWidth = getWidth();
                resizeStartX = -1;
                setCursor(Cursor.getDefaultCursor());
            }
            dragging = false;
        }

        @Override
        public void mouseMoved(@NotNull final MouseEvent e) {
            // 悬停左缘时提示可调宽
            setCursor(e.getX() <= RESIZE_ZONE_WIDTH
                    ? Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)
                    : Cursor.getDefaultCursor());
        }

        @Override
        public void mouseWheelMoved(@NotNull final java.awt.event.MouseWheelEvent e) {
            // 滚轮滚动为默认行为：滚轮单位（行）× 编辑器行高 → 文档像素偏移滚动
            editor.getScrollingModel().scrollVertically(
                    editor.getScrollingModel().getVisibleArea().y + e.getUnitsToScroll() * editor.getLineHeight());
        }
    }
}
