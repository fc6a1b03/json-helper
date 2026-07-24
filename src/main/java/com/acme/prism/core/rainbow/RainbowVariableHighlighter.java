package com.acme.prism.core.rainbow;

import com.acme.prism.core.settings.PluginSettingsState;
import com.acme.prism.core.settings.ProjectDisposableService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 彩虹变量高亮监听器（每个编辑器一个实例）。
 * <p>监听文档变化，经 {@link Alarm} 防抖后在 ReadAction 后台线程遍历 Java PSI，
 * 收集局部变量/方法参数的声明点与引用点，再切回 EDT 通过
 * {@link RangeHighlighter} 按变量名着色（同名变量全文件同色）。</p>
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
final class RainbowVariableHighlighter implements DocumentListener {
    /**
     * 防抖延迟（毫秒）：连续输入合并为一次重算
     */
    private static final int REFRESH_DELAY_MILLIS = 300;

    /**
     * 大文件保护阈值（行数）：超过该值直接跳过着色，避免全量 PSI 遍历与 resolve 拖慢 IDE
     */
    private static final int MAX_LINE_COUNT = 20000;

    /**
     * 高亮层权重：略高于语法高亮层，保证前景色可见
     */
    private static final int HIGHLIGHT_LAYER_WEIGHT = 1;

    private final Editor editor;
    private final Project project;
    private final Document document;
    private final Alarm refreshAlarm;
    private final List<RangeHighlighter> activeHighlighters = new ArrayList<>();
    private volatile boolean disposed;

    /**
     * 构造监听器并注册到编辑器文档。
     *
     * @param editor  编辑器实例
     * @param project 所属项目
     */
    RainbowVariableHighlighter(@NotNull final Editor editor, @NotNull final Project project) {
        this.editor = editor;
        this.project = project;
        this.document = editor.getDocument();
        // 后台线程 Alarm：防抖回调直接在池线程执行，PSI 遍历不占用 EDT
        this.refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ProjectDisposableService.getInstance(project));
        // 生命周期由 EditorFactoryListener.editorReleased 显式管理，不挂 parent disposable 避免双重移除
        this.document.addDocumentListener(this);
        // 延迟调度首次刷新，避免在编辑器尚未初始化完成时访问 PSI
        scheduleRefresh();
    }

    @Override
    public void documentChanged(@NotNull final DocumentEvent event) {
        scheduleRefresh();
    }

    /**
     * 清理监听器和高亮。
     */
    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        document.removeDocumentListener(this);
        refreshAlarm.cancelAllRequests();
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
     * 刷新高亮：在读取动作中收集变量出现位置，然后切回 EDT 应用。
     * <p>本方法在 Alarm 的后台池线程执行；非 Java 文件、功能关闭或超大文件时仅做清空。</p>
     */
    private void refreshHighlight() {
        if (editor.isDisposed() || project.isDisposed()) {
            return;
        }
        if (!isEnabled() || document.getLineCount() > MAX_LINE_COUNT) {
            ApplicationManager.getApplication().invokeLater(this::clearHighlighters);
            return;
        }
        // 索引未就绪（dumb mode）时 resolve 会抛 IndexNotReadyException；
        // 改走 smart mode 读取：索引未就绪时自动排队，就绪后再执行，保证打开项目后变量着色自动出现
        DumbService.getInstance(project).runReadActionInSmartMode(() -> {
            if (editor.isDisposed() || project.isDisposed()) {
                return;
            }
            final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (!(psiFile instanceof PsiJavaFile)) {
                ApplicationManager.getApplication().invokeLater(this::clearHighlighters);
                return;
            }
            final List<VariableOccurrence> occurrences = collectOccurrences(psiFile);
            ApplicationManager.getApplication().invokeLater(() -> applyHighlighters(occurrences));
        });    }

    /**
     * 遍历 Java PSI，收集局部变量/参数的声明点与引用点文本范围。
     * <p>必须在 ReadAction 中调用（resolve 仅允许在读取动作内执行）。</p>
     *
     * @param javaFile Java PSI 文件
     * @return 变量出现位置列表
     */
    @NotNull
    private List<VariableOccurrence> collectOccurrences(@NotNull final PsiFile javaFile) {
        final List<VariableOccurrence> occurrences = new ArrayList<>();
        PsiTreeUtil.processElements(javaFile, element -> {
            if (element instanceof PsiVariable variable
                    && (variable instanceof PsiLocalVariable || variable instanceof PsiParameter)) {
                // 声明点：仅取名称标识符范围
                final PsiElement nameIdentifier = variable.getNameIdentifier();
                if (Objects.nonNull(nameIdentifier)) {
                    addOccurrence(occurrences, nameIdentifier.getTextRange(), variable.getName());
                }
            } else if (element instanceof PsiReferenceExpression reference
                    && !(element instanceof PsiMethodCallExpression)) {
                // 引用点：仅处理无限定符或 this 限定的引用，resolve 结果须为局部变量/参数
                final PsiExpression qualifier = reference.getQualifierExpression();
                if (Objects.isNull(qualifier) || qualifier instanceof PsiThisExpression) {
                    try {
                        final PsiElement resolved = reference.resolve();
                        if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) {
                            final PsiElement nameElement = reference.getReferenceNameElement();
                            if (Objects.nonNull(nameElement)) {
                                addOccurrence(occurrences, nameElement.getTextRange(), reference.getReferenceName());
                            }
                        }
                    } catch (final IndexNotReadyException ignored) {
                        // 索引重建竞态期单个引用解析失败：跳过该引用，不阻断整体着色
                    }
                }
            }
            return Boolean.TRUE;
        });
        return occurrences;
    }

    /**
     * 向结果列表追加一条变量出现位置（跳过非法范围与空名称）。
     */
    private static void addOccurrence(@NotNull final List<VariableOccurrence> occurrences,
                                      final TextRange range, final String name) {
        if (Objects.isNull(name) || range.getStartOffset() < 0 || range.getLength() <= 0) {
            return;
        }
        occurrences.add(new VariableOccurrence(range.getStartOffset(), range.getEndOffset(), name));
    }

    /**
     * 将计算出的变量出现位置应用到编辑器（必须在 EDT 执行）。
     * <p>采用全清重建策略：先移除旧高亮，再批量添加，不维护增量状态。</p>
     */
    private void applyHighlighters(@NotNull final List<VariableOccurrence> occurrences) {
        if (editor.isDisposed()) {
            return;
        }
        clearHighlighters();
        if (occurrences.isEmpty()) {
            return;
        }
        final MarkupModel markupModel = editor.getMarkupModel();
        final Map<String, TextAttributes> attributesCache = new HashMap<>();
        final int textLength = document.getTextLength();
        for (final VariableOccurrence occurrence : occurrences) {
            // PSI 与文档可能不同步，应用前做边界校验
            if (occurrence.startOffset() < 0 || occurrence.endOffset() > textLength
                    || occurrence.startOffset() >= occurrence.endOffset()) {
                continue;
            }
            final TextAttributes attributes = attributesCache.computeIfAbsent(
                    occurrence.name(), RainbowVariableHighlighter::createAttributes);
            activeHighlighters.add(markupModel.addRangeHighlighter(
                    occurrence.startOffset(),
                    occurrence.endOffset(),
                    HighlighterLayer.SYNTAX + HIGHLIGHT_LAYER_WEIGHT,
                    attributes,
                    HighlighterTargetArea.EXACT_RANGE));
        }
    }

    /**
     * 按变量名构造文本属性：仅设置前景色，其余属性留空以保留原有语法样式。
     */
    @NotNull
    private static TextAttributes createAttributes(@NotNull final String name) {
        final TextAttributes attributes = new TextAttributes();
        attributes.setForegroundColor(RainbowVariablePalette.forName(name));
        return attributes;
    }

    /**
     * 清理当前编辑器上的全部高亮（必须在 EDT 执行）。
     */
    private void clearHighlighters() {
        if (editor.isDisposed()) {
            activeHighlighters.clear();
            return;
        }
        final MarkupModel markupModel = editor.getMarkupModel();
        for (final RangeHighlighter highlighter : activeHighlighters) {
            markupModel.removeHighlighter(highlighter);
        }
        activeHighlighters.clear();
    }

    /**
     * 判断功能是否开启。
     */
    private boolean isEnabled() {
        return PluginSettingsState.getInstance().rainbowVariableEnabled;
    }

    /**
     * 变量出现位置数据。
     *
     * @param startOffset 起始偏移
     * @param endOffset   结束偏移
     * @param name        变量名（取色依据）
     */
    private record VariableOccurrence(int startOffset, int endOffset, String name) {
    }
}
