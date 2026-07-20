package com.acme.json.helper.core.rainbow;

import com.acme.json.helper.core.settings.PluginSettingsState;
import com.acme.json.helper.core.settings.ProjectDisposableService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 监听光标位置变化并刷新彩虹括号配对高亮。
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
final class RainbowCaretListener implements CaretListener {
    /**
     * 防抖延迟（毫秒）：连续快速移动光标时合并为一次重绘
     */
    private static final int REFRESH_DELAY_MILLIS = 50;

    private final Editor editor;
    private final RainbowBracketPairHighlighter highlighter;
    private final Alarm refreshAlarm;
    private final RangeHighlighter[] activeHighlighters = new RangeHighlighter[2];
    private RainbowBracketPairHighlighter.BracePair lastPair;

    /**
     * 构造监听器并注册到编辑器。
     *
     * @param editor  编辑器实例
     * @param project 所属项目
     */
    RainbowCaretListener(@NotNull final Editor editor, @NotNull final Project project) {
        this.editor = editor;
        this.highlighter = new RainbowBracketPairHighlighter(editor);
        this.refreshAlarm = new Alarm(ProjectDisposableService.getInstance(project));
        editor.getCaretModel().addCaretListener(this);
        // 延迟到下一次 EDT 调度，避免在编辑器尚未初始化完成时访问 PSI/Highlighter
        scheduleRefresh();
    }

    @Override
    public void caretPositionChanged(@NotNull final CaretEvent event) {
        // caret 事件可能在 write action 中触发，先延迟到 EDT 空闲时再做读取/重绘
        scheduleRefresh();
    }

    /**
     * 清理监听器和高亮。
     */
    void dispose() {
        refreshAlarm.cancelAllRequests();
        editor.getCaretModel().removeCaretListener(this);
        clearHighlighters();
    }

    /**
     * 调度一次高亮刷新，连续事件会被合并。
     */
    private void scheduleRefresh() {
        if (editor.isDisposed()) {
            return;
        }
        refreshAlarm.cancelAllRequests();
        refreshAlarm.addRequest(this::refreshHighlight, REFRESH_DELAY_MILLIS);
    }

    /**
     * 刷新高亮：在读取动作中计算新配对，然后切回 EDT 应用。
     */
    private void refreshHighlight() {
        if (editor.isDisposed() || !highlighter.isAvailable()) {
            clearHighlighters();
            return;
        }
        if (!isEnabled()) {
            clearHighlighters();
            return;
        }
        ApplicationManager.getApplication().runReadAction(() -> {
            if (editor.isDisposed()) {
                return;
            }
            final int offset = editor.getCaretModel().getOffset();
            final RainbowBracketPairHighlighter.BracePair pair = highlighter.findClosestBracePair(offset);
            ApplicationManager.getApplication().invokeLater(() -> applyHighlighters(pair));
        });
    }

    /**
     * 将计算出的括号对应用到编辑器（必须在 EDT 执行）。
     * <p>如果配对未发生变化，会复用已有高亮器，避免重复的 MarkupModel 操作。</p>
     */
    private void applyHighlighters(final RainbowBracketPairHighlighter.BracePair pair) {
        if (editor.isDisposed()) {
            return;
        }
        if (Objects.equals(pair, lastPair)) {
            return;
        }
        clearHighlighters();
        lastPair = pair;
        if (Objects.nonNull(pair)) {
            final RangeHighlighter[] created = highlighter.highlightPair(pair);
            activeHighlighters[0] = created[0];
            activeHighlighters[1] = created[1];
        }
    }

    /**
     * 清理当前编辑器上的高亮。
     */
    private void clearHighlighters() {
        highlighter.eraseHighlighters(activeHighlighters);
        activeHighlighters[0] = null;
        activeHighlighters[1] = null;
        lastPair = null;
    }

    /**
     * 判断功能是否开启。
     */
    private boolean isEnabled() {
        return PluginSettingsState.getInstance().rainbowBracketPairEnabled;
    }
}
