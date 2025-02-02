package com.acme.json.helper.ui.action;

import com.acme.json.helper.common.Clipboard;
import com.acme.json.helper.common.UastSupported;
import com.acme.json.helper.core.json.JsonFormatter;
import com.acme.json.helper.core.parser.ClassParser;
import com.acme.json.helper.settings.PluginSettings;
import com.acme.json.helper.ui.notice.Notifier;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
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
    /** 加载语言资源文件 */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * 更新动作可见性状态
     *
     * @param e 行动事件
     */
    @Override
    @SuppressWarnings("DuplicatedCode")
    public void update(@NotNull AnActionEvent e) {
        // 确认配置已开启
        if (Boolean.FALSE.equals(PluginSettings.of().copyJson)) {
            e.getPresentation().setEnabledAndVisible(Boolean.FALSE);
            return;
        }
        // 确认窗口项目正常
        final Project project = e.getProject();
        if (Objects.isNull(project)) {
            e.getPresentation().setEnabledAndVisible(Boolean.FALSE);
            return;
        }
        // 确认文件索引已完成
        if (DumbService.isDumb(project)) {
            e.getPresentation().setEnabledAndVisible(Boolean.FALSE);
            return;
        }
        // 确认文件PSI正常
        final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (Objects.isNull(psiFile)) {
            e.getPresentation().setEnabledAndVisible(Boolean.FALSE);
            Notifier.notifyWarn(BUNDLE.getString("bean.copy.json.warn"), e.getProject());
            return;
        }
        e.getPresentation().setEnabledAndVisible(
                // 检查文件的UAST语言支持 且 存在有效的类上下文
                UastSupported.of(psiFile) && UastSupported.hasValidClassContext(e.getData(CommonDataKeys.EDITOR), psiFile)
        );
    }

    /**
     * 主执行逻辑
     *
     * @param e 行动事件
     */
    @Override
    public void actionPerformed(final @NotNull AnActionEvent e) {
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
        // 将 JSON 字符串放入剪贴板
        Clipboard.copy(new JsonFormatter().process(ClassParser.classToMap(psiClass)));
        // 提示用户
        Notifier.notifyInfo(BUNDLE.getString("bean.copy.json.success"), project);
    }
}