package com.acme.json.helper.ui.action;

import cn.hutool.core.lang.Opt;
import com.acme.json.helper.common.ActionEventCheck;
import com.acme.json.helper.common.UastSupported;
import com.acme.json.helper.core.json.JsonFormatter;
import com.acme.json.helper.core.parser.ClassParser;
import com.acme.json.helper.core.settings.PluginSettings;
import com.acme.json.helper.ui.editor.JsonEditorPushProvider;
import com.acme.json.helper.ui.notice.Notifier;
import com.alibaba.fastjson2.JSON;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 统一JSON处理操作，整合Java类转JSON和JSON文本处理功能
 * <br/>
 * 功能逻辑：
 * 1. 在Java文件上下文：生成类结构对应的JSON并推送到JSON编辑器
 * 2. 在JSON文本选区上下文：发送到JSON辅助工具窗口进行可视化处理
 *
 *
 * @author 拒绝者
 * @since 2025-01-28
 */
public class JsonHelperAction extends AnAction {
    /** 加载语言资源文件 */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * 更新动作可见性状态
     * <br/>
     * 判断逻辑：
     * 1. 当前为支持UAST解析的Java文件（光标位于类定义内）
     * 2. 或存在有效的JSON文本选区
     *
     * @param e 行动事件
     */
    @Override
    public void update(@NotNull final AnActionEvent e) {
        // 分步检查
        switch (ActionEventCheck.stepByStepInspection(e, PluginSettings.of().jsonHelper)) {
            // 执行错误状态
            case ActionEventCheck.Check.Failed failed -> failed.action().run();
            // 确认最终状态
            case ActionEventCheck.Check.Success ignored -> e.getPresentation().setEnabledAndVisible(
                    checkJavaContextValidity(e) || checkJsonSelectionValidity(e)
            );
        }
    }

    /**
     * 主执行逻辑
     * <br/>
     * 执行优先级：
     * 1. 优先处理Java文件上下文
     * 2. 其次处理JSON文本选区
     *
     * @param e 行动事件
     */
    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
        if (checkJavaContextValidity(e)) {
            // 处理Java文件上下文
            handleJavaClassContext(e.getProject(), e.getData(CommonDataKeys.EDITOR), e.getData(CommonDataKeys.PSI_FILE));
        } else if (checkJsonSelectionValidity(e)) {
            // 处理JSON文本选区
            handleJsonSelection(e);
        }
    }

    /**
     * 检查Java上下文有效性
     * <br/>
     * 本方法执行链式安全检查，验证以下条件：
     * 1. 确认配置已开启
     * 2. 文件已完成索引
     * 3. 是UAST语言支持
     * 4. 是有效的`PsiFile`
     * 5. 是`Java`文件
     *
     * @param e 行动事件
     * @return true表示当前处于有效的Java类上下文
     */
    private boolean checkJavaContextValidity(@NotNull final AnActionEvent e) {
        // 确认文件PSI正常
        final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (Objects.isNull(psiFile)) {
            Notifier.notifyWarn(BUNDLE.getString("bean.copy.json.warn"), e.getProject());
            return Boolean.FALSE;
        }
        // 检查文件的UAST语言支持 且 存在有效的类上下文
        return UastSupported.of(psiFile) && UastSupported.hasValidClassContext(e.getData(CommonDataKeys.EDITOR), psiFile);
    }

    /* ########################### 上下文验证方法 ########################### */

    /**
     * 验证当前编辑器选区内容是否为合法JSON格式
     * <br/>
     * 本方法执行链式安全检查，验证以下条件：
     * 1. 确认配置已开启
     * 2. 文件已完成索引
     * 3. 存在有效的编辑器实例
     * 4. 编辑器存在文本选区
     * 5. 选中文本内容符合JSON语法规范
     *
     * @param e 动作事件上下文，包含编辑器、项目等运行时数据
     * @return 验证结果：
     *         - true:  当前存在有效JSON选区
     *         - false: 无编辑器/无选区/选区文本不符合JSON规范
     * @implNote 该方法使用{@link Opt}进行空安全操作，避免潜在的NPE问题
     */
    private boolean checkJsonSelectionValidity(@NotNull final AnActionEvent e) {
        // 从事件中获取编辑器引用
        return Opt.ofNullable(e.getData(CommonDataKeys.EDITOR))
                // 转换为选区模型
                .map(Editor::getSelectionModel)
                // 过滤存在选区的情况
                .filter(SelectionModel::hasSelection)
                // 执行JSON语法验证
                .map(sel -> JSON.isValid(sel.getSelectedText()))
                .orElse(Boolean.FALSE);
    }

    /**
     * 处理Java类到JSON的转换逻辑
     *
     * @param project 项目
     * @param editor 编辑器
     * @param psiFile psi文件
     */
    private void handleJavaClassContext(final Project project, final Editor editor, final PsiFile psiFile) {
        // 定位当前光标所在的PSI类
        final PsiClass targetClass = UastSupported.locatePsiClass(editor, psiFile);
        if (Objects.isNull(targetClass)) {
            Notifier.notifyWarn(BUNDLE.getString("bean.copy.json.warn"), project);
            return;
        }
        // 将内容推送到JSON编辑器
        JsonEditorPushProvider.pushToJsonEditor(project, generateClassJson(targetClass));
    }

    /* ########################### Java类处理逻辑 ########################### */

    /**
     * 生成类结构JSON
     *
     * @param psiClass psi等级
     * @return {@link String }
     */
    private String generateClassJson(final PsiClass psiClass) {
        return new JsonFormatter().process(ClassParser.classToMap(psiClass));
    }

    /**
     * 处理JSON文本选区逻辑
     *
     * @param e 行动事件
     */
    private void handleJsonSelection(@NotNull final AnActionEvent e) {
        JsonEditorPushProvider.pushToJsonEditor(e.getProject(), processSelectedJson(e));
    }

    /* ########################### JSON文本处理逻辑 ########################### */

    /**
     * 处理并规范化选中的JSON文本内容
     * <br/>
     * 本方法执行以下处理流程：
     * 1. 从编辑器事件中获取当前选区文本
     * 2. 对多行文本执行公共缩进去除（适用于格式化后的JSON）
     * 3. 移除文本首尾的空白字符
     *
     * @param e 动作事件上下文，包含编辑器实例等运行时数据
     * @return 处理后的标准化文本：
     *         - 当存在有效选区时：返回经缩进清理和修剪的文本
     *         - 无编辑器/无选区时：返回空字符串
     * @implNote 该方法保证返回非null值，且不会修改原始编辑器内容
     */
    private String processSelectedJson(@NotNull final AnActionEvent e) {
        // 空安全获取编辑器引用
        return Opt.ofNullable(e.getData(CommonDataKeys.EDITOR))
                // 转换为选区模型
                .map(Editor::getSelectionModel)
                // 获取原始选区内容（可能为null）
                .map(SelectionModel::getSelectedText)
                // 消除多行文本的公共缩进
                .map(text -> text.stripIndent().trim())
                // 默认返回空字符串（任何环节为空时）
                .orElse("");
    }
}