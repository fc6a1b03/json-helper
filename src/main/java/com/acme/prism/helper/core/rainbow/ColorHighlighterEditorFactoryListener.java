package com.acme.prism.core.rainbow;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 编辑器工厂监听器：在编辑器创建时挂载颜色字面量高亮监听器，并在编辑器释放时清理。
 *
 * @author 拒绝者
 * @date 2026-07-20
 */
public final class ColorHighlighterEditorFactoryListener implements EditorFactoryListener {
    /**
     * 维护编辑器与监听器的映射，确保释放时可被正确 dispose。
     */
    private static final Map<Editor, ColorHighlightListener> LISTENERS = new ConcurrentHashMap<>();

    @Override
    public void editorCreated(@NotNull final EditorFactoryEvent event) {
        final Editor editor = event.getEditor();
        final Project project = editor.getProject();
        if (Objects.isNull(project)) {
            return;
        }
        LISTENERS.computeIfAbsent(editor, e -> new ColorHighlightListener(e, project));
    }

    @Override
    public void editorReleased(@NotNull final EditorFactoryEvent event) {
        final Editor editor = event.getEditor();
        final ColorHighlightListener listener = LISTENERS.remove(editor);
        if (Objects.nonNull(listener)) {
            listener.dispose();
        }
    }
}
