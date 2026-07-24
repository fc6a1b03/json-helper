package com.acme.prism.core.rainbow;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * 彩虹括号配对高亮器。
 * <p>基于平台 {@link BraceMatchingUtil} 定位光标附近最近的可配对括号，并通过
 * {@link RangeHighlighter} 在编辑器中实时高亮。</p>
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
public final class RainbowBracketPairHighlighter {
    /**
     * 高亮层权重，略高于选区层，保证可见且不覆盖选区
     */
    private static final int HIGHLIGHT_LAYER_WEIGHT = 1;

    private final Editor editor;
    private final Document document;
    private final MarkupModelEx markupModel;

    /**
     * 构造高亮器。
     *
     * @param editor 编辑器实例
     */
    public RainbowBracketPairHighlighter(@NotNull final Editor editor) {
        this.editor = editor;
        this.document = editor.getDocument();
        this.markupModel = (MarkupModelEx) editor.getMarkupModel();
    }

    /**
     * 当前编辑器是否支持括号配对高亮。
     *
     * @return true 表示支持
     */
    public boolean isAvailable() {
        return !editor.isDisposed() && Objects.nonNull(editor.getProject());
    }

    /**
     * 查找距离光标最近的配对括号。
     *
     * @param offset 光标偏移量
     * @return 配对括号信息；未找到返回 null
     */
    @Nullable
    public BracePair findClosestBracePair(final int offset) {
        if (editor.isDisposed() || offset < 0 || offset > document.getTextLength()) {
            return null;
        }
        final Project project = editor.getProject();
        if (Objects.isNull(project)) {
            return null;
        }
        final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (Objects.isNull(psiFile)) {
            return null;
        }
        final FileType fileType = psiFile.getFileType();
        if (Objects.isNull(fileType)) {
            return null;
        }
        final CharSequence text = document.getImmutableCharSequence();
        // 优先尝试左侧最近的左括号，再尝试右侧最近的右括号
        final BracePair byLeft = findByLeftBrace(offset, fileType, text);
        if (Objects.nonNull(byLeft) && byLeft.rightOffset() >= offset) {
            return byLeft;
        }
        final BracePair byRight = findByRightBrace(offset, fileType, text);
        if (Objects.nonNull(byRight) && byRight.leftOffset() <= offset) {
            return byRight;
        }
        return null;
    }

    /**
     * 高亮给定的括号对。
     *
     * @param pair 括号对
     * @return 高亮器数组 [左, 右]
     */
    @NotNull
    public RangeHighlighter[] highlightPair(@NotNull final BracePair pair) {
        if (editor.isDisposed()) {
            return new RangeHighlighter[2];
        }
        final var leftAttributes = editor.getColorsScheme().getAttributes(pair.attributesKey());
        if (Objects.isNull(leftAttributes)) {
            return new RangeHighlighter[2];
        }
        final RangeHighlighter left = markupModel.addRangeHighlighter(
                pair.leftOffset(),
                pair.leftOffset() + pair.leftLength(),
                HighlighterLayer.SELECTION + HIGHLIGHT_LAYER_WEIGHT,
                leftAttributes,
                HighlighterTargetArea.EXACT_RANGE);
        final RangeHighlighter right = markupModel.addRangeHighlighter(
                pair.rightOffset(),
                pair.rightOffset() + pair.rightLength(),
                HighlighterLayer.SELECTION + HIGHLIGHT_LAYER_WEIGHT,
                leftAttributes,
                HighlighterTargetArea.EXACT_RANGE);
        return new RangeHighlighter[]{left, right};
    }

    /**
     * 移除已有高亮。
     *
     * @param highlighters 待移除的高亮器集合
     */
    public void eraseHighlighters(@NotNull final RangeHighlighter[] highlighters) {
        if (editor.isDisposed()) {
            return;
        }
        for (final RangeHighlighter highlighter : highlighters) {
            if (Objects.nonNull(highlighter)) {
                markupModel.removeHighlighter(highlighter);
            }
        }
    }

    /**
     * 安全获取编辑器高亮器。
     */
    @Nullable
    private EditorHighlighter safeHighlighter() {
        return ((EditorEx) editor).getHighlighter();
    }

    /**
     * 从光标向左查找第一个左括号并正向匹配到对应的右括号。
     */
    @Nullable
    private BracePair findByLeftBrace(final int offset, @NotNull final FileType fileType, @NotNull final CharSequence text) {
        final EditorHighlighter highlighter = safeHighlighter();
        if (Objects.isNull(highlighter)) {
            return null;
        }
        final HighlighterIterator iterator = highlighter.createIterator(offset);
        while (!iterator.atEnd()) {
            if (BraceMatchingUtil.isLBraceToken(iterator, text, fileType)) {
                final IElementType leftType = iterator.getTokenType();
                final int leftOffset = iterator.getStart();
                final int leftLength = iterator.getEnd() - leftOffset;
                final int rightOffset = findMatchingRightOffset(leftOffset, leftType, fileType, text);
                if (rightOffset >= offset) {
                    final HighlighterIterator rightIterator = highlighter.createIterator(rightOffset);
                    final int rightLength = rightIterator.getEnd() - rightIterator.getStart();
                    return new BracePair(leftOffset, rightOffset, leftLength, rightLength, keyFor(text.subSequence(leftOffset, leftOffset + leftLength)));
                }
                // 匹配到的右括号在光标左侧，说明不是目标对；继续向左寻找
            }
            iterator.retreat();
        }
        return null;
    }

    /**
     * 从光标向右查找第一个右括号并反向匹配到对应的左括号。
     */
    @Nullable
    private BracePair findByRightBrace(final int offset, @NotNull final FileType fileType, @NotNull final CharSequence text) {
        final EditorHighlighter highlighter = safeHighlighter();
        if (Objects.isNull(highlighter)) {
            return null;
        }
        final HighlighterIterator iterator = highlighter.createIterator(offset);
        while (!iterator.atEnd()) {
            if (BraceMatchingUtil.isRBraceToken(iterator, text, fileType)) {
                final IElementType rightType = iterator.getTokenType();
                final int rightOffset = iterator.getStart();
                final int rightLength = iterator.getEnd() - rightOffset;
                final int leftOffset = findMatchingLeftOffset(rightOffset, rightType, fileType, text);
                if (leftOffset >= 0 && leftOffset <= offset) {
                    final HighlighterIterator leftIterator = highlighter.createIterator(leftOffset);
                    final int leftLength = leftIterator.getEnd() - leftIterator.getStart();
                    return new BracePair(leftOffset, rightOffset, leftLength, rightLength, keyFor(text.subSequence(leftOffset, leftOffset + leftLength)));
                }
                // 匹配到的左括号在光标右侧，说明不是目标对；继续向右寻找
            }
            iterator.advance();
        }
        return null;
    }

    /**
     * 从指定左括号位置正向扫描，找到与其配对的右括号偏移量。
     */
    private int findMatchingRightOffset(final int leftOffset, final IElementType leftType, @NotNull final FileType fileType, @NotNull final CharSequence text) {
        final EditorHighlighter highlighter = safeHighlighter();
        if (Objects.isNull(highlighter)) {
            return -1;
        }
        final HighlighterIterator iterator = highlighter.createIterator(leftOffset);
        final Deque<IElementType> stack = new ArrayDeque<>();
        while (!iterator.atEnd()) {
            final IElementType type = iterator.getTokenType();
            if (BraceMatchingUtil.isLBraceToken(iterator, text, fileType)) {
                stack.push(type);
            } else if (BraceMatchingUtil.isRBraceToken(iterator, text, fileType)) {
                if (stack.isEmpty()) {
                    return -1;
                }
                final IElementType top = stack.peek();
                if (!BraceMatchingUtil.isPairBraces(top, type, fileType)) {
                    return -1;
                }
                stack.pop();
                if (stack.isEmpty()) {
                    return iterator.getStart();
                }
            }
            iterator.advance();
        }
        return -1;
    }

    /**
     * 从指定右括号位置反向扫描，找到与其配对的左括号偏移量。
     */
    private int findMatchingLeftOffset(final int rightOffset, final IElementType rightType, @NotNull final FileType fileType, @NotNull final CharSequence text) {
        final EditorHighlighter highlighter = safeHighlighter();
        if (Objects.isNull(highlighter)) {
            return -1;
        }
        final HighlighterIterator iterator = highlighter.createIterator(rightOffset);
        final Deque<IElementType> stack = new ArrayDeque<>();
        while (!iterator.atEnd()) {
            final IElementType type = iterator.getTokenType();
            if (BraceMatchingUtil.isRBraceToken(iterator, text, fileType)) {
                stack.push(type);
            } else if (BraceMatchingUtil.isLBraceToken(iterator, text, fileType)) {
                if (stack.isEmpty()) {
                    return -1;
                }
                final IElementType top = stack.peek();
                if (!BraceMatchingUtil.isPairBraces(type, top, fileType)) {
                    return -1;
                }
                stack.pop();
                if (stack.isEmpty()) {
                    return iterator.getStart();
                }
            }
            iterator.retreat();
        }
        return -1;
    }

    /**
     * 根据左括号文本选取颜色键。
     */
    @NotNull
    private static TextAttributesKey keyFor(@NotNull final CharSequence braceText) {
        return switch (braceText.toString()) {
            case "(" -> RainbowBracketPairColors.PARENTHESIS;
            case "[" -> RainbowBracketPairColors.BRACKET;
            default -> RainbowBracketPairColors.BRACE;
        };
    }

    /**
     * 括号对数据。
     *
     * @param leftOffset     左括号起始偏移
     * @param rightOffset    右括号起始偏移
     * @param leftLength     左括号长度
     * @param rightLength    右括号长度
     * @param attributesKey  使用的颜色键
     */
    public record BracePair(int leftOffset, int rightOffset, int leftLength, int rightLength,
                            TextAttributesKey attributesKey) {
    }
}
