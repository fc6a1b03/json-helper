package com.acme.json.helper.ui.action.json;

import com.acme.json.helper.ui.dialog.CreateClassDialog;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 从JSON创建类
 * @author 拒绝者
 * @date 2025-04-29
 */
public class CreateClassFromJsonAction extends AnAction {
    /** 加载语言资源文件 */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
        e.getPresentation().setText(BUNDLE.getString("action.create.class.from.json.text"));
        e.getPresentation().setDescription(BUNDLE.getString("action.create.class.from.json.desc"));
        e.getPresentation().setEnabledAndVisible(Objects.nonNull(e.getProject()) && Objects.nonNull(this.getPsiDirectory(e.getDataContext().getData(CommonDataKeys.PSI_ELEMENT))));
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
        final Project project = e.getProject();
        final PsiDirectory targetDirectory = this.getPsiDirectory(e.getDataContext().getData(CommonDataKeys.PSI_ELEMENT));
        if (Objects.isNull(project) || Objects.isNull(targetDirectory)) {
            return;
        }
        // 打开 创建类对话框
        new CreateClassDialog(project, targetDirectory).show();
    }

    /**
     * 获取PSI目录
     * @param selectedElement 选定元素
     * @return {@link PsiDirectory }
     */
    private PsiDirectory getPsiDirectory(final PsiElement selectedElement) {
        return switch (selectedElement) {
            case final PsiDirectory directory -> directory;
            case final PsiFile file -> file.getContainingDirectory();
            case final PsiClass file -> file.getContainingFile().getContainingDirectory();
            case null, default -> null;
        };
    }
}