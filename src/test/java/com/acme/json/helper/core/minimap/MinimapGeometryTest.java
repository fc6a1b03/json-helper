package com.acme.json.helper.core.minimap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * minimap 几何换算工具单元测试
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
class MinimapGeometryTest {

    /**
     * 每逻辑行缩略像素高，与 MinimapRenderer 保持一致
     */
    private static final int PPI = 3;

    @Test
    @DisplayName("无折叠：y 与行号直通换算，高度为行数乘行像素")
    void mapsDirectlyWithoutFolds() {
        final var noFolds = List.<int[]>of();
        assertAll(
                () -> assertEquals(30, MinimapGeometry.documentHeight(10, noFolds, PPI), "10 行应为 30 像素"),
                () -> assertEquals(15, MinimapGeometry.logicalLineToY(5, noFolds, PPI), "第 5 行应映射到 y=15"),
                () -> assertEquals(0, MinimapGeometry.logicalLineToY(0, noFolds, PPI), "第 0 行应映射到 y=0"),
                () -> assertEquals(4, MinimapGeometry.yToLogicalLine(14, noFolds, 10, PPI), "y=14 应落在第 4 行"),
                () -> assertEquals(5, MinimapGeometry.yToLogicalLine(15, noFolds, 10, PPI), "y=15 应落在第 5 行"),
                () -> assertEquals(0, MinimapGeometry.yToLogicalLine(0, noFolds, 10, PPI), "y=0 应落在第 0 行")
        );
    }

    @Test
    @DisplayName("单折叠区间：隐藏行不占高度，y 与行号按补偿换算")
    void compensatesSingleFoldRegion() {
        // 折叠 [2,5]：隐藏 3/4/5 三行，可见 7 行
        final var folds = MinimapGeometry.mergeFoldIntervals(List.of(new int[]{2, 5}));
        assertAll(
                () -> assertEquals(21, MinimapGeometry.documentHeight(10, folds, PPI), "可见 7 行应为 21 像素"),
                () -> assertEquals(6, MinimapGeometry.logicalLineToY(2, folds, PPI), "折叠首行 2 保持可见，y=6"),
                () -> assertEquals(9, MinimapGeometry.logicalLineToY(6, folds, PPI), "行 6 补偿 3 个隐藏行，y=9"),
                () -> assertEquals(18, MinimapGeometry.logicalLineToY(9, folds, PPI), "末行 9 应映射到 y=18"),
                () -> assertEquals(2, MinimapGeometry.yToLogicalLine(6, folds, 10, PPI), "y=6 应为可见行 2"),
                () -> assertEquals(2, MinimapGeometry.yToLogicalLine(8, folds, 10, PPI), "y=8 仍属行 2 的色带"),
                () -> assertEquals(6, MinimapGeometry.yToLogicalLine(9, folds, 10, PPI), "y=9 应跳过隐藏行落到行 6"),
                () -> assertEquals(7, MinimapGeometry.yToLogicalLine(12, folds, 10, PPI), "y=12 应为行 7"),
                () -> assertEquals(9, MinimapGeometry.yToLogicalLine(20, folds, 10, PPI), "y=20 应为末行 9")
        );
    }

    @Test
    @DisplayName("多折叠区间：多个隐藏段累计补偿")
    void compensatesMultipleFoldRegions() {
        // 折叠 [2,3] 与 [6,8]：隐藏 {3} 与 {7,8} 共 3 行，可见 9 行
        final var folds = MinimapGeometry.mergeFoldIntervals(List.of(new int[]{2, 3}, new int[]{6, 8}));
        assertAll(
                () -> assertEquals(27, MinimapGeometry.documentHeight(12, folds, PPI), "可见 9 行应为 27 像素"),
                () -> assertEquals(9, MinimapGeometry.logicalLineToY(4, folds, PPI), "行 4 补偿 1 行，y=9"),
                () -> assertEquals(15, MinimapGeometry.logicalLineToY(6, folds, PPI), "行 6 补偿 1 行，y=15"),
                () -> assertEquals(18, MinimapGeometry.logicalLineToY(9, folds, PPI), "行 9 补偿 3 行，y=18"),
                () -> assertEquals(24, MinimapGeometry.logicalLineToY(11, folds, PPI), "行 11 补偿 3 行，y=24"),
                () -> assertEquals(4, MinimapGeometry.yToLogicalLine(9, folds, 12, PPI), "y=9 应为行 4"),
                () -> assertEquals(6, MinimapGeometry.yToLogicalLine(15, folds, 12, PPI), "y=15 应为行 6"),
                () -> assertEquals(9, MinimapGeometry.yToLogicalLine(18, folds, 12, PPI), "y=18 应跳过 {7,8} 落到行 9"),
                () -> assertEquals(11, MinimapGeometry.yToLogicalLine(24, folds, 12, PPI), "y=24 应为行 11")
        );
    }

    @Test
    @DisplayName("区间合并：嵌套、乱序、相接区间正确合并，单行折叠被丢弃")
    void mergesOverlappingIntervals() {
        assertAll(
                () -> {
                    // 嵌套 [2,8] 与 [4,6]：合并为 [2,8]，隐藏 6 行
                    final var merged = MinimapGeometry.mergeFoldIntervals(List.of(new int[]{2, 8}, new int[]{4, 6}));
                    assertEquals(1, merged.size(), "嵌套区间应合并为 1 个");
                    assertEquals(6, MinimapGeometry.countHiddenLines(merged), "隐藏行应为 3..8 共 6 行");
                },
                () -> {
                    // 乱序输入 [5,7]、[2,3]：排序后不合并
                    final var merged = MinimapGeometry.mergeFoldIntervals(List.of(new int[]{5, 7}, new int[]{2, 3}));
                    assertEquals(2, merged.size(), "不相交区间应保持 2 个");
                    assertEquals(2, merged.getFirst()[0], "排序后首个区间应从行 2 开始");
                    assertEquals(3, MinimapGeometry.countHiddenLines(merged), "隐藏行应为 {3} 与 {6,7} 共 3 行");
                },
                () -> {
                    // 首尾相接 [2,3] 与 [3,7]：合并为 [2,7]，隐藏行并集 {3,4,5,6,7}
                    final var merged = MinimapGeometry.mergeFoldIntervals(List.of(new int[]{2, 3}, new int[]{3, 7}));
                    assertEquals(1, merged.size(), "相接区间应合并为 1 个");
                    assertEquals(5, MinimapGeometry.countHiddenLines(merged), "隐藏行并集应为 5 行");
                },
                () -> {
                    // 单行折叠 [3,3] 不隐藏任何行，应被丢弃
                    final var merged = MinimapGeometry.mergeFoldIntervals(List.of(new int[]{3, 3}));
                    assertTrue(merged.isEmpty(), "单行折叠无隐藏行，应被丢弃");
                }
        );
    }

    @Test
    @DisplayName("越界钳制：负 y 钳到首行，超高 y 钳到末行，空文档返回 0")
    void clampsOutOfRangeY() {
        final var folds = MinimapGeometry.mergeFoldIntervals(List.of(new int[]{2, 5}));
        assertAll(
                () -> assertEquals(0, MinimapGeometry.yToLogicalLine(-6, folds, 10, PPI), "负 y 应钳制到行 0"),
                () -> assertEquals(9, MinimapGeometry.yToLogicalLine(21, folds, 10, PPI), "超出总高的 y 应钳制到末行 9"),
                () -> assertEquals(9, MinimapGeometry.yToLogicalLine(1000, folds, 10, PPI), "极大 y 应钳制到末行 9"),
                () -> assertEquals(0, MinimapGeometry.yToLogicalLine(9, folds, 0, PPI), "空文档应返回 0"),
                () -> assertEquals(0, MinimapGeometry.logicalLineToY(-1, folds, PPI), "负行号 y 不应为负")
        );
    }

    @Test
    @DisplayName("边界行：折叠从第 0 行开始时首行仍可见，其后行补偿")
    void handlesFoldStartingAtFirstLine() {
        // 折叠 [0,4]：隐藏 1..4 共 4 行，可见 6 行
        final var folds = MinimapGeometry.mergeFoldIntervals(List.of(new int[]{0, 4}));
        assertAll(
                () -> assertEquals(18, MinimapGeometry.documentHeight(10, folds, PPI), "可见 6 行应为 18 像素"),
                () -> assertEquals(0, MinimapGeometry.logicalLineToY(0, folds, PPI), "折叠首行 0 仍可见，y=0"),
                () -> assertEquals(3, MinimapGeometry.logicalLineToY(5, folds, PPI), "行 5 补偿 4 个隐藏行，y=3"),
                () -> assertEquals(0, MinimapGeometry.yToLogicalLine(0, folds, 10, PPI), "y=0 应为行 0"),
                () -> assertEquals(0, MinimapGeometry.yToLogicalLine(2, folds, 10, PPI), "y=2 仍属行 0 的色带"),
                () -> assertEquals(5, MinimapGeometry.yToLogicalLine(3, folds, 10, PPI), "y=3 应跳过 1..4 落到行 5"),
                () -> assertEquals(9, MinimapGeometry.yToLogicalLine(15, folds, 10, PPI), "y=15 应为末行 9")
        );
    }

    @Test
    @DisplayName("隐藏行判定：折叠首行可见，区间 (start, end] 内为隐藏")
    void detectsHiddenLines() {
        final var folds = MinimapGeometry.mergeFoldIntervals(List.of(new int[]{2, 5}));
        assertAll(
                () -> assertFalse(MinimapGeometry.isHidden(2, folds), "折叠首行 2 应可见"),
                () -> assertTrue(MinimapGeometry.isHidden(3, folds), "行 3 应隐藏"),
                () -> assertTrue(MinimapGeometry.isHidden(5, folds), "行 5 应隐藏"),
                () -> assertFalse(MinimapGeometry.isHidden(6, folds), "行 6 应可见")
        );
    }
}
