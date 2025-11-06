package com.acme.json.helper.ui.action.screenshot;

import cn.hutool.core.lang.Opt;
import com.acme.json.helper.core.notice.Notifier;
import com.acme.json.helper.core.screenshot.CodeScreenshotSupplier;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 代码截图
 * @author 拒绝者
 * @date 2025-11-06
 */
public final class CodeScreenshot extends AnAction {
    /**
     * 加载资源文件
     */
    private static final ResourceBundle bundle = ResourceBundle.getBundle("messages.JsonHelperBundle");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        Opt.ofNullable(event).map(AnActionEvent::getProject).filter(Objects::nonNull)
                .ifPresentOrElse(
                        project -> Opt.ofNullable(event.getData(CommonDataKeys.EDITOR))
                                .filter(editor -> editor.getSelectionModel().hasSelection())
                                .ifPresent(editor -> this.takeSnapshot(editor, project)),
                        () -> Notifier.notifyError(bundle.getString("show.clipboard.select.failed.notification"), event.getProject())
                );
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
        Opt.ofNullable(CodeScreenshotSupplier.createImage(editor)).filter(Objects::nonNull)
                .ifPresent(image -> {
                    if (CodeScreenshotSupplier.tryCopyToClipboard(image)) {
                        CodeScreenshotSupplier.showCopySuccessNotification(image, project);
                    } else {
                        CodeScreenshotSupplier.showClipboardFailedNotification(image, project);
                    }
                });
    }
}