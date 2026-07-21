package com.acme.json.helper.core.minimap;

import com.acme.json.helper.core.settings.PluginSettingsState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 代码缩略图显示面板（每编辑器一个，挂载于编辑器组件右侧）。
 * <p>以编辑器背景色显式铺底（滚动容器默认底色发白，不能依赖透明透出），
 * 仅绘制缩略图、错误条纹与视口框；左缘拖拽可在 40~200 像素间调节宽度并持久化；右键菜单提供功能开关。</p>
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
     * 错误条纹半透明叠加系数（右移 24 位 alpha 后按此系数衰减，避免整行横条盖过代码纹理）
     */
    private static final float ERROR_STRIPE_ALPHA_SCALE = 0.5f;
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
     * 拖动起点：鼠标按下时的面板 y 坐标（-1 表示未在拖动）
     */
    private int dragStartY = -1;
    /**
     * 拖动起点：编辑器垂直滚动像素偏移基准
     */
    private int dragStartScrollOffset;
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
        // 平移采样：缩略图整体超出面板时，让视口在绘制窗口内尽量居中，并钳制到 [0, 文档缩略高度 - 绘制高度]
        sourceStartY = state.documentHeight() <= drawHeight ? 0
                : Math.max(0, Math.min(state.viewportStart() - (drawHeight - state.viewportHeight()) / 2,
                        state.documentHeight() - drawHeight));
        effectiveScale = 1.0;
        if (Objects.nonNull(image)) {
            // 源矩形取图像全宽（110 列），目标按面板宽缩放——宽度调节零渲染成本（Java2D 采样缩放）；
            // 最近邻插值保持像素边界（默认双线性会把相邻字符点混成灰白雾），SRC_OVER 0.8 让纹理再淡一档
            final var sourceEndY = Math.min(sourceStartY + drawHeight, Math.min(state.documentHeight(), image.getHeight()));
            if (sourceEndY > sourceStartY && graphics instanceof Graphics2D graphics2D) {
                final var defaultComposite = graphics2D.getComposite();
                // BICUBIC 插值：轻度缩小时加权保留全部列（NEAREST 抽样会随机丢弃 1/3 列导致字符断裂）
                graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics2D.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, IMAGE_ALPHA));
                graphics2D.drawImage(image, 0, 0, width, sourceEndY - sourceStartY,
                        0, sourceStartY, image.getWidth(), sourceEndY, null);
                graphics2D.setComposite(defaultComposite);
            }
        }
        if (PluginSettingsState.getInstance().minimapErrorStripeEnabled) {
            paintErrorStripes(graphics, width);
        }
        // 视口框：仅 8% 透明圆角填充（无边框线，几乎透明但可辨识当前可视区域）
        if (state.viewportHeight() > 0) {
            final var viewportY = (int) ((state.viewportStart() - sourceStartY) * effectiveScale);
            graphics.setColor(VIEWPORT_FILL);
            graphics.fillRoundRect(0, viewportY, width, (int) (state.viewportHeight() * effectiveScale), 5, 5);
        }
    }

    /**
     * 绘制错误/警告线条的整行条纹（半透明横条，颜色取平台 error-stripe 标记色）。
     * <p>遍历编辑器 MarkupModel 的 highlighter，仅处理带 error-stripe 颜色的项；
     * 数量与编辑器诊断条同级（判断为字段直读，滚动重绘频率下开销可忽略）。</p>
     */
    private void paintErrorStripes(@NotNull final Graphics graphics, final int width) {
        final var document = editor.getDocument();
        for (final var highlighter : editor.getMarkupModel().getAllHighlighters()) {
            final var stripeColor = highlighter.getErrorStripeMarkColor(editor.getColorsScheme());
            if (Objects.isNull(stripeColor)) {
                continue;
            }
            final var startLine = document.getLineNumber(Math.min(highlighter.getStartOffset(), document.getTextLength()));
            final var endLine = document.getLineNumber(Math.min(highlighter.getEndOffset(), document.getTextLength()));
            final var y = (int) ((view.logicalLineToY(startLine) - sourceStartY) * effectiveScale);
            final var yEnd = (int) ((view.logicalLineToY(endLine) + view.pixelsPerLine() - sourceStartY) * effectiveScale);
            graphics.setColor(withAlphaScale(stripeColor));
            graphics.fillRect(0, Math.max(y, 0), width, Math.min(yEnd, getHeight()) - Math.max(y, 0));
        }
    }

    /**
     * 颜色按系数衰减透明度（条纹叠在代码纹理上不刺眼）。
     */
    private static Color withAlphaScale(@NotNull final Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(1, (int) (color.getAlpha() * ERROR_STRIPE_ALPHA_SCALE)));
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
            editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
            // 记录拖动基准：以跳转后的新滚动位置为原点累计后续拖动 delta
            dragStartY = e.getY();
            dragStartScrollOffset = editor.getScrollingModel().getVisibleArea().y;
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
            if (dragStartY < 0 || !SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            // 换算链：面板缩略坐标 delta ÷ 拉伸系数 ÷ 每行缩略像素 × 编辑器行高 → 文档像素 delta，基于拖动起点偏移滚动
            final var deltaY = e.getY() - dragStartY;
            editor.getScrollingModel().scrollVertically(
                    dragStartScrollOffset + (int) (deltaY / effectiveScale * editor.getLineHeight() / view.pixelsPerLine()));
        }

        @Override
        public void mouseReleased(@NotNull final MouseEvent e) {
            if (resizeStartX >= 0) {
                // 调宽结束：持久化当前宽度
                PluginSettingsState.getInstance().minimapWidth = getWidth();
                resizeStartX = -1;
                setCursor(Cursor.getDefaultCursor());
            }
            dragStartY = -1;
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
