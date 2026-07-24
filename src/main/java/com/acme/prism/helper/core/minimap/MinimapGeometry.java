package com.acme.prism.core.minimap;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * minimap 几何换算工具：负责"缩略图 y 坐标 ↔ 逻辑行号"在折叠补偿下的纯逻辑换算。
 * <p>折叠区间用 {@code int[]{startLine, endLine}} 表示：折叠首行 startLine 仍可见
 * （编辑器中该行显示占位符），(startLine, endLine] 为隐藏行，不占缩略高度。</p>
 * <p>无 IDE 依赖，可独立单元测试。</p>
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
public final class MinimapGeometry {
    private MinimapGeometry() {
    }

    /**
     * 规范化折叠区间：丢弃无隐藏行的区间，按首行排序并合并重叠/首尾相接的区间。
     *
     * @param intervals 原始折叠区间列表（元素为 [startLine, endLine]）
     * @return 合并后的有序区间列表（新列表，不修改入参）
     */
    @NotNull
    public static List<int[]> mergeFoldIntervals(@NotNull final List<int[]> intervals) {
        final var sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparingInt((final int[] interval) -> interval[0])
                .thenComparingInt(interval -> interval[1]));
        final var merged = new ArrayList<int[]>();
        for (final var interval : sorted) {
            // 单行折叠（endLine <= startLine）不隐藏任何行，直接丢弃
            if (interval[1] <= interval[0]) {
                continue;
            }
            if (!merged.isEmpty()) {
                final var last = merged.getLast();
                // 重叠或首尾相接（next.start <= last.end）时合并，隐藏行并集等价
                if (interval[0] <= last[1]) {
                    last[1] = Math.max(last[1], interval[1]);
                    continue;
                }
            }
            merged.add(new int[]{interval[0], interval[1]});
        }
        return merged;
    }

    /**
     * 统计全部隐藏行数。
     */
    public static int countHiddenLines(@NotNull final List<int[]> merged) {
        var count = 0;
        for (final var interval : merged) {
            count += interval[1] - interval[0];
        }
        return count;
    }

    /**
     * 统计严格小于指定逻辑行的隐藏行数。
     */
    public static int countHiddenBefore(final int line, @NotNull final List<int[]> merged) {
        var count = 0;
        for (final var interval : merged) {
            // 隐藏行范围为 (start, end]，取其中 < line 的部分
            final var hiddenEnd = Math.min(interval[1], line - 1);
            if (hiddenEnd > interval[0]) {
                count += hiddenEnd - interval[0];
            }
        }
        return count;
    }

    /**
     * 判断逻辑行是否被折叠隐藏。
     */
    public static boolean isHidden(final int line, @NotNull final List<int[]> merged) {
        for (final var interval : merged) {
            if (line > interval[0] && line <= interval[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * 文档缩略总高度（像素）= 可见行数 × 每行像素高。
     */
    public static int documentHeight(final int totalLines, @NotNull final List<int[]> merged, final int pixelsPerLine) {
        return Math.max(0, totalLines - countHiddenLines(merged)) * pixelsPerLine;
    }

    /**
     * 逻辑行号 → 缩略图 y 坐标。
     * <p>隐藏行没有独立缩略带，会映射到折叠后第一个可见行的 y（单调不减，调用方应配合
     * {@link #isHidden} 跳过隐藏行的绘制）。</p>
     */
    public static int logicalLineToY(final int line, @NotNull final List<int[]> merged, final int pixelsPerLine) {
        return Math.max(0, line - countHiddenBefore(line, merged)) * pixelsPerLine;
    }

    /**
     * 缩略图 y 坐标 → 逻辑行号（越界钳制到合法行 [0, totalLines-1]）。
     */
    public static int yToLogicalLine(final int y, @NotNull final List<int[]> merged, final int totalLines, final int pixelsPerLine) {
        if (totalLines <= 0 || pixelsPerLine <= 0) {
            return 0;
        }
        final var visibleCount = totalLines - countHiddenLines(merged);
        if (visibleCount <= 0) {
            return 0;
        }
        final var row = Math.clamp(y / pixelsPerLine, 0, visibleCount - 1);
        // 将"第 row 个可见行"还原为逻辑行号：沿途累加隐藏行数
        var hiddenBefore = 0;
        for (final var interval : merged) {
            final var candidate = row + hiddenBefore;
            if (candidate <= interval[0]) {
                return Math.min(candidate, totalLines - 1);
            }
            hiddenBefore += interval[1] - interval[0];
        }
        return Math.min(row + hiddenBefore, totalLines - 1);
    }
}
