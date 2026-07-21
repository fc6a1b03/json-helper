package com.acme.json.helper.core.minimap;

import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;

/**
 * 代码缩略图视图（已冻结接口）：向显示面板（{@link MinimapPanel}）提供缩略图图像与坐标换算能力。
 * <p>由渲染核心实现（{@link MinimapRenderer}）；显示侧只依赖本接口，不引用具体实现类。</p>
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
public interface MinimapView {
    /**
     * 缩略图（未就绪返回 null；内部原地更新，调用方直接 drawImage 即可，不要修改图片内容）
     */
    @Nullable BufferedImage image();

    /**
     * 文档缩略总高度（像素，含折叠补偿后）
     */
    int documentHeight();

    /**
     * 每逻辑行的缩略像素高（固定 2）
     */
    int pixelsPerLine();

    /**
     * 缩略图 y 坐标 → 逻辑行号（含折叠补偿；越界钳制到合法行）
     */
    int yToLogicalLine(int y);

    /**
     * 逻辑行号 → 缩略图 y 坐标（含折叠补偿）
     */
    int logicalLineToY(int line);
}
