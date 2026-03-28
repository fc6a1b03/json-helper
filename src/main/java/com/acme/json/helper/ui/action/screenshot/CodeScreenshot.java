package com.acme.json.helper.ui.action.screenshot;

import cn.hutool.core.lang.Opt;
import com.acme.json.helper.core.notice.Notifier;
import com.acme.json.helper.core.screenshot.CodeScreenshotSupplier;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 代码截图
 * @author 拒绝者
 * @date 2025-11-06
 */
public final class CodeScreenshot extends DumbAwareAction {
    /**
     * 加载资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        final Project project = event.getProject();
        final Editor editor = PlatformDataKeys.EDITOR.getData(event.getDataContext());
        event.getPresentation().setText(BUNDLE.getString("action.class.copy.image.text"));
        event.getPresentation().setDescription(BUNDLE.getString("action.class.copy.image.description"));
        event.getPresentation().setEnabledAndVisible(Objects.nonNull(project) && Objects.nonNull(editor) && editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        final Project project = event.getProject();
        if (Objects.isNull(project)) return;
        final Editor editor = PlatformDataKeys.EDITOR.getData(event.getDataContext());
        if (Objects.isNull(editor)) {
            Notifier.notifyError(BUNDLE.getString("show.clipboard.select.failed.notification"), project);
            return;
        }
        this.takeSnapshot(editor, project);
    }

    /**
     * 拍快照
     * @param editor  编辑器
     * @param project 项目
     */
    private void takeSnapshot(final Editor editor, final Project project) {
        if (Objects.isNull(editor) || Objects.isNull(project)) {
            return;
        }
        new Task.Backgroundable(project, BUNDLE.getString("action.class.copy.image.text"), Boolean.FALSE) {
            private java.awt.image.BufferedImage image;

            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                indicator.setIndeterminate(Boolean.TRUE);
                this.image = CodeScreenshotSupplier.createImage(editor);
            }

            @Override
            public void onSuccess() {
                Opt.ofNullable(this.image).filter(Objects::nonNull)
                        .ifPresentOrElse(image -> {
                            if (CodeScreenshotSupplier.tryCopyToClipboard(image)) {
                                CodeScreenshotSupplier.showCopySuccessNotification(image, project);
                            } else {
                                CodeScreenshotSupplier.showClipboardFailedNotification(image, project);
                            }
                        }, () -> Notifier.notifyError(BUNDLE.getString("show.clipboard.render.failed.notification"), project));
            }

            @Override
            public void onThrowable(@NotNull final Throwable error) {
                super.onThrowable(error);
                Notifier.notifyError(BUNDLE.getString("show.clipboard.render.failed.notification"), project);
            }
        }.queue();
    }
}
