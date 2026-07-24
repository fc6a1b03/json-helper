package com.acme.prism.common;

import cn.hutool.core.lang.Opt;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbService;

import java.util.Objects;

/**
 * 动作事件检查
 *
 * @author 拒绝者
 * @date 2025-02-03
 */
public class ActionEventCheck {
    /**
     * 分步检查
     *
     * @param e             行动事件
     * @param settingsState 插件状态
     * @return {@link Check }
     */
    @SuppressWarnings({"DataFlowIssue"})
    public static Check stepByStepInspection(final AnActionEvent e, final boolean settingsState) {
        if (!settingsState || Objects.isNull(e.getProject())) {
            return new Check.Failed(() -> disabled(e));
        }
        return Opt.ofNullable(e.getProject())
                .filter(project -> !DumbService.isDumb(project))
                .<Check>map(_ -> new Check.Success())
                .orElseGet(() -> new Check.Failed(() -> disabled(e)));
    }

    /**
     * 设置已禁用
     *
     * @param e 行动事件
     */
    public static void disabled(final AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(Boolean.FALSE);
    }

    /**
     * 分步检查处理类
     *
     * @author 拒绝者
     * @date 2025-02-03
     */
    public sealed interface Check permits Check.Failed, Check.Success {
        record Success() implements Check {
        }

        record Failed(Runnable action) implements Check {
        }
    }
}
