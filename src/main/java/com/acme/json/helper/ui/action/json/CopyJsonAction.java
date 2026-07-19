package com.acme.json.helper.ui.action.json;

import com.acme.json.helper.common.ActionEventCheck;
import com.acme.json.helper.common.Clipboard;
import com.acme.json.helper.common.UastSupported;
import com.acme.json.helper.core.json.JsonFormatter;
import com.acme.json.helper.core.notice.Notifier;
import com.acme.json.helper.core.parser.ClassParser;
import com.acme.json.helper.core.settings.PluginSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 复制JSON操作
 * @author 拒绝者
 * @date 2025-01-25
 */
public class CopyJsonAction extends AnAction {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * 更新动作可见性状态
     * @param e 行动事件
     */
    @Override
    public void update(@NotNull final AnActionEvent e) {
        // 分步检查
        switch (ActionEventCheck.stepByStepInspection(e, PluginSettings.of().copyJson)) {
            // 执行错误状态
            case final ActionEventCheck.Check.Failed failed -> failed.action().run();
            // 确认最终状态
            case final ActionEventCheck.Check.Success ignored -> {
                // 确认文件PSI正常
                final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
                if (Objects.isNull(psiFile)) {
                    ActionEventCheck.disabled(e);
                    return;
                }
                // 确认最终状态设置
                e.getPresentation().setEnabledAndVisible(
                        UastSupported.of(psiFile) && UastSupported.hasValidClassContext(e.getData(CommonDataKeys.EDITOR), psiFile)
                );
            }
        }
        e.getPresentation().setText(BUNDLE.getString("action.class.copy.json.text"));
        e.getPresentation().setDescription(BUNDLE.getString("action.class.copy.json.description"));
    }

    /**
     * 主执行逻辑
     * @param e 行动事件
     */
    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
        final Project project = e.getProject();
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (Objects.isNull(project) || Objects.isNull(editor) || Objects.isNull(psiFile)) {
            return;
        }
        // 获取当前选中的类
        final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiFile.findElementAt(editor.getCaretModel().getOffset()), PsiClass.class);
        if (Objects.isNull(psiClass)) {
            return;
        }
        new Task.Backgroundable(project, BUNDLE.getString("action.class.copy.json.text"), Boolean.FALSE) {
            private String generatedJson;

            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                this.generatedJson = new JsonFormatter().process(ClassParser.classToMap(psiClass));
            }

            @Override
            public void onSuccess() {
                Clipboard.copy(this.generatedJson);
                Notifier.notifyInfo(BUNDLE.getString("bean.copy.json.success"), project);
            }
        }.queue();
    }
}
