package com.acme.json.helper.common;

import cn.hutool.core.lang.Opt;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbService;

import java.util.ResourceBundle;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 动作事件检查
 *
 * @author 拒绝者
 * @date 2025-02-03
 */
public class ActionEventCheck {
    /** 加载语言资源文件 */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");

    /**
     * 分步检查
     *
     * @param e 行动事件
     * @param settingsState 插件状态
     * @return {@link Check }
     */
    public static Check stepByStepInspection(final AnActionEvent e, final boolean settingsState) {
        return Stream.<Supplier<Check>>of(
                        // 检查插件配置
                        () -> settingsState
                                ? new Check.Success()
                                : new Check.Failed(() -> disabled(e)),
                        // 检查项目有效性
                        () -> Opt.ofNullable(e.getProject())
                                .<Check>map(p -> new Check.Success())
                                .orElse(new Check.Failed(() -> disabled(e))),
                        // 检查索引状态
                        () -> Opt.ofNullable(e.getProject())
                                .filter(p -> Boolean.FALSE.equals(DumbService.isDumb(p)))
                                .<Check>map(p -> new Check.Success())
                                .orElse(new Check.Failed(() -> disabled(e)))
                )
                .map(Supplier::get)
                .filter(Check.Failed.class::isInstance)
                .findFirst()
                .orElse(new Check.Success());
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