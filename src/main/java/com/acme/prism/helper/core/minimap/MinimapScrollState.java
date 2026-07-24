package com.acme.prism.core.minimap;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import org.jetbrains.annotations.NotNull;

/**
 * minimap 滚动状态快照：面板据此定位视口框与绘制窗口。
 *
 * @param viewportStart  视口缩略起点（像素 y）
 * @param viewportHeight 视口缩略高度（像素）
 * @param documentHeight 文档缩略总高度（像素）
 * @param drawHeight     绘制窗口高度（像素，文档总高与面板可视高取小）
 * @author 拒绝者
 * @date 2026-07-20
 */
public record MinimapScrollState(int viewportStart, int viewportHeight, int documentHeight, int drawHeight) {
    /**
     * 由编辑器当前滚动位置与 minimap 视图计算滚动状态。
     * <p>编辑器滚动坐标是视觉行系（软换行把一个逻辑行折成多个视觉行），minimap 按逻辑行渲染，
     * 必须沿 视觉 y → 视觉行 → 逻辑行 → 缩略 y 换算，否则软换行密集区（如 Markdown 表格）视口框严重错位；
     * 绘制窗口高 = min(文档缩略总高, 编辑器组件可视高)。</p>
     *
     * @param editor 编辑器实例
     * @param view   minimap 视图
     * @return 滚动状态快照
     */
    @NotNull
    public static MinimapScrollState of(@NotNull final Editor editor, @NotNull final MinimapView view) {
        final var visibleArea = editor.getScrollingModel().getVisibleArea();
        final var pixelsPerLine = view.pixelsPerLine();
        final var startLine = editor.visualToLogicalPosition(new VisualPosition(editor.yToVisualLine(visibleArea.y), 0)).line;
        final var endLine = editor.visualToLogicalPosition(new VisualPosition(editor.yToVisualLine(visibleArea.y + visibleArea.height), 0)).line;
        final var documentHeight = view.documentHeight();
        final var viewportStart = view.logicalLineToY(startLine);
        // 视口下沿所在逻辑行部分可见，补一行缩略高使框高覆盖到该行底部
        final var viewportHeight = Math.max(pixelsPerLine,
                Math.min(documentHeight, view.logicalLineToY(endLine) + pixelsPerLine) - viewportStart);
        final var drawHeight = Math.min(documentHeight, editor.getComponent().getHeight());
        return new MinimapScrollState(viewportStart, viewportHeight, documentHeight, drawHeight);
    }
}
