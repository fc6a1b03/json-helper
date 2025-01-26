package com.acme.json.helper.ui.action;

import cn.hutool.core.lang.Opt;
import com.acme.json.helper.common.Clipboard;
import com.acme.json.helper.core.json.JsonFormatter;
import com.acme.json.helper.core.parser.ClassParser;
import com.acme.json.helper.ui.notice.Notifier;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UastLanguagePlugin;

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

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(uastSupported(e.getData(CommonDataKeys.PSI_FILE)));
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
        final Project project = e.getProject();
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (Objects.isNull(project) || Objects.isNull(editor) || Objects.isNull(psiFile)) {
            return;
        }
        if (Boolean.FALSE.equals(uastSupported(psiFile))) {
            Notifier.notifyWarn(BUNDLE.getString("bean.copy.json.warn"), project);
            return;
        }
        // 获取当前选中的类
        final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiFile.findElementAt(editor.getCaretModel().getOffset()), PsiClass.class);
        if (Objects.isNull(psiClass)) {
            return;
        }
        // 将 JSON 字符串放入剪贴板
        Clipboard.copy(new JsonFormatter().process(ClassParser.classToMap(psiClass)));
        // 提示用户
        Notifier.notifyInfo(BUNDLE.getString("bean.copy.json.success"), project);
    }

    /**
     * 是否受支持
     * @param psiFile PSI文件
     * @return boolean
     */
    private boolean uastSupported(final PsiFile psiFile) {
        return Opt.ofNullable(psiFile)
                .map(item -> UastLanguagePlugin.Companion.getInstances()
                        .stream().filter(Objects::nonNull)
                        .anyMatch(l -> l.isFileSupported(psiFile.getName())))
                .orElse(Boolean.FALSE);
    }
}