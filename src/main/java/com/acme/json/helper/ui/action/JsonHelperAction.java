package com.acme.json.helper.ui.action;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.UastSupported;
import com.acme.json.helper.core.json.JsonFormatter;
import com.acme.json.helper.core.parser.ClassParser;
import com.acme.json.helper.settings.PluginSettings;
import com.acme.json.helper.ui.MainToolWindowFactory;
import com.acme.json.helper.ui.notice.Notifier;
import com.alibaba.fastjson2.JSON;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
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
    @SuppressWarnings("DuplicatedCode")
    public void update(@NotNull AnActionEvent e) {
        // 确认配置已开启
        if (Boolean.FALSE.equals(PluginSettings.of().jsonHelper)) {
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
        e.getPresentation().setEnabledAndVisible(
                checkJavaContextValidity(e) || checkJsonSelectionValidity(e)
        );
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
        pushToJsonEditor(project, generateClassJson(targetClass));
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
        pushToJsonEditor(e.getProject(), processSelectedJson(e));
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

    /**
     * 将内容推送到JSON编辑器
     *
     * @param project 项目
     * @param content 内容
     */
    private void pushToJsonEditor(final Project project, final String content) {
        // 构建编辑器配置对象，关联当前项目及JSON辅助工具窗口
        final EditorConfig config = new EditorConfig(
                project,
                Opt.ofNullable(project)
                        // 获取项目关联的`Json Helper`工具窗口实例
                        .map(p -> ToolWindowManager.getInstance(p).getToolWindow("Json Helper"))
                        .orElse(null)
        );
        // 工具窗口对象
        final ToolWindow toolWindow = config.toolWindow();
        // 工具窗口未打开则会自动打开
        Opt.ofNullable(config.toolWindow())
                .filter(item -> Boolean.FALSE.equals(item.isVisible()))
                .ifPresent(ToolWindow::show);
        // 窗口激活时执行内容填充
        toolWindow.activate(() -> {
            // 编辑器内容更新策略：
            // 1. 优先查找现有可用编辑器（如已打开的JSON预览标签页）
            // 2. 存在则直接更新内容，否则创建新标签页并初始化内容
            ApplicationManager.getApplication().invokeLater(() ->
                    findReusableEditor(config).ifPresentOrElse(
                            // 更新现有编辑器
                            editor -> updateEditorContent(config, editor, content),
                            // 新建标签页流程
                            () -> createNewEditorTab(config, content)
                    )
            );
        });
    }

    /* ########################### 统一编辑器推送逻辑 ########################### */

    /**
     * 查找可复用的JSON编辑器实例
     * <br/>
     * 本方法在工具窗口的现有标签页中查找符合以下条件的编辑器组件：
     * 1. 组件容器为{@link JPanel}类型
     * 2. 包含有效的{@link EditorTextField}编辑器实例
     * 3. 编辑器当前没有文本内容（空编辑器）
     * <br/>
     * 查找策略说明：
     * - 优先复用空白编辑器，避免创建过多冗余标签页
     * - 按标签页打开顺序进行查找（从最早到最新）
     * - 仅检查直接包含在JPanel中的编辑器组件
     *
     * @param config 编辑器配置对象，包含工具窗口引用等上下文信息
     * @return 包装在{@link Optional}中的可用编辑器实例，找不到时返回{@link Optional#empty()}
     */
    private Optional<EditorTextField> findReusableEditor(@NotNull final EditorConfig config) {
        // 获取工具窗口的内容管理器中的所有内容
        return Arrays.stream(config.toolWindow().getContentManager().getContents())
                // 转换工具窗口内容为UI组件（每个Content对应一个标签页）
                .map(Content::getComponent)
                // 过滤出JPanel容器（根据UI结构约定编辑器位于面板中）
                .filter(JPanel.class::isInstance)
                // 安全类型转换（已通过上方过滤器确保类型）
                .map(JPanel.class::cast)
                // 在面板中深度查找编辑器组件（可能返回null）
                .map(this::deepFindEditor)
                // 过滤掉未找到编辑器的情况
                .filter(Objects::nonNull)
                // 选择空白编辑器以便内容复用（避免覆盖已有数据）
                .filter(editor -> StrUtil.isEmpty(editor.getText()))
                // 返回首个符合条件的编辑器（按标签页打开顺序）
                .findFirst();
    }

    /* ########################### 编辑器操作工具方法 ########################### */

    /**
     * 深度查找编辑器组件（递归搜索容器结构）
     *
     * @param container 容器
     * @return {@link EditorTextField }
     */
    private EditorTextField deepFindEditor(@NotNull final Container container) {
        // 遍历容器的直接子组件
        for (final Component comp : container.getComponents()) {
            // 类型匹配检查：发现目标编辑器组件时立即返回
            if (comp instanceof final EditorTextField editor) {
                return editor;
            }
            // 递归处理子容器：当组件是容器时继续向下搜索
            else if (comp instanceof final Container subContainer) {
                // 递归调用搜索子容器，保留非空结果
                final EditorTextField result = deepFindEditor(subContainer);
                if (Objects.nonNull(result)) return result;
            }
        }
        return null;
    }

    /**
     * 更新编辑器内容
     *
     * @param editor 编辑器
     * @param text 文本
     */
    private void updateEditorContent(@NotNull final EditorConfig config, @NotNull final EditorTextField editor, @NotNull final String text) {
        ApplicationManager.getApplication().invokeLater(() -> {
            // 将内容写入编辑器
            editor.setText(text);
            // 激活编辑器文本格式化
            com.acme.json.helper.ui.editor.Editor.reformat(editor);
            // 激活对应页签
            Opt.ofNullable(Opt.ofNullable(editor.getParent())
                            .map(Component::getParent).filter(Objects::nonNull)
                            .map(Component::getParent).orElse(null))
                    .ifPresent(item -> {
                        // 获取内容管理器
                        final ContentManager contentManager = config.toolWindow().getContentManager();
                        // 切换到包含编辑器的页签
                        Arrays.stream(contentManager.getContents())
                                .sequential()
                                .filter(ArrayUtil::isNotEmpty)
                                .filter(content -> {
                                    return ObjectUtil.equal(content.getComponent(), item);
                                })
                                .findFirst().ifPresent(content -> contentManager.setSelectedContent(content, Boolean.TRUE));
                    });
        });
    }

    /**
     * 创建新编辑器标签页
     *
     * @param config 编辑器配置
     * @param text 文本
     */
    private void createNewEditorTab(@NotNull final EditorConfig config, @NotNull final String text) {
        // 创建新的工具窗口标签页（该操作会触发UI更新）
        new MainToolWindowFactory().createNewTab(config.project(), config.toolWindow());
        ApplicationManager.getApplication().invokeLater(() -> {
            // 获取窗口所有内容管理
            final Content[] contents = config.toolWindow().getContentManager().getContents();
            if (contents.length == 0) return;
            // 获取最新创建的标签页
            final Content newContent = contents[contents.length - 1];
            // 更新编辑器内容
            final EditorTextField editor = deepFindEditor(newContent.getComponent());
            if (Objects.nonNull(editor)) {
                // 在找到的编辑器中更新内容
                updateEditorContent(config, editor, text);
            }
        });
    }

    /**
     * 编辑器配置记录类
     *
     * @param project    当前项目实例
     * @param toolWindow JSON工具窗口实例
     */
    private record EditorConfig(Project project, ToolWindow toolWindow) {
        /**
         * 验证配置有效性
         * @return true表示配置有效可用于UI操作
         */
        public boolean isValid() {
            return Objects.nonNull(project) && Boolean.FALSE.equals(project.isDisposed()) &&
                    Objects.nonNull(toolWindow) && Boolean.FALSE.equals(toolWindow.isDisposed());
        }
    }
}