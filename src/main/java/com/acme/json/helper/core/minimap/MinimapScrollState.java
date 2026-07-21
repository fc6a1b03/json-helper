package com.acme.json.helper.core.minimap;

import com.intellij.openapi.editor.Editor;
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
     * <p>视口缩略起点/高度 = 可视区域 y/高 ÷ 编辑器行高 × 每行缩略像素；
     * 绘制窗口高 = min(文档缩略总高, 编辑器组件可视高)——面板与编辑器组件同高，
     * 决定缩略图在面板上的最大绘制高度（滚动模式下超出部分按视口平移采样）。</p>
     *
     * @param editor 编辑器实例
     * @param view   minimap 视图
     * @return 滚动状态快照
     */
    @NotNull
    public static MinimapScrollState of(@NotNull final Editor editor, @NotNull final MinimapView view) {
        final var visibleArea = editor.getScrollingModel().getVisibleArea();
        final var lineHeight = editor.getLineHeight();
        final var pixelsPerLine = view.pixelsPerLine();
        // 行高恒为正，防御性判零避免极端状态除零
        final var viewportStart = lineHeight > 0 ? visibleArea.y / lineHeight * pixelsPerLine : 0;
        final var viewportHeight = lineHeight > 0 ? visibleArea.height / lineHeight * pixelsPerLine : 0;
        final var documentHeight = view.documentHeight();
        final var drawHeight = Math.min(documentHeight, editor.getComponent().getHeight());
        return new MinimapScrollState(viewportStart, viewportHeight, documentHeight, drawHeight);
    }
}
