package com.acme.json.helper.ui.editor;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.ui.MainToolWindowFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON编辑器推送
 * @author 拒绝者
 * @date 2025-04-23
 */
public class JsonEditorPushProvider {
    /**
     * 将内容推送到JSON编辑器
     * @param project 项目
     * @param content 内容
     */
    public static void pushToJsonEditor(final Project project, final String content) {
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
                .filter(item -> !item.isVisible())
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
     * @param config 编辑器配置对象，包含工具窗口引用等上下文信息
     * @return 包装在{@link Optional}中的可用编辑器实例，找不到时返回{@link Optional#empty()}
     */
    private static Optional<EditorTextField> findReusableEditor(@NotNull final EditorConfig config) {
        // 获取工具窗口的内容管理器中的所有内容
        return Arrays.stream(config.toolWindow().getContentManager().getContents())
                // 转换工具窗口内容为UI组件（每个Content对应一个标签页）
                .map(Content::getComponent)
                // 过滤出JPanel容器（根据UI结构约定编辑器位于面板中）
                .filter(JPanel.class::isInstance)
                // 安全类型转换（已通过上方过滤器确保类型）
                .map(JPanel.class::cast)
                // 在面板中深度查找编辑器组件（可能返回null）
                .map(JsonEditorPushProvider::deepFindEditor)
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
     * @param container 容器
     * @return {@link EditorTextField }
     */
    public static EditorTextField deepFindEditor(@NotNull final Container container) {
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
     * @param editor 编辑器
     * @param text   文本
     */
    private static void updateEditorContent(@NotNull final EditorConfig config, @NotNull final EditorTextField editor, @NotNull final String text) {
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
                                .filter(content -> ObjectUtil.equal(content.getComponent(), item))
                                .findFirst().ifPresent(content -> contentManager.setSelectedContent(content, Boolean.TRUE));
                    });
        });
    }

    /**
     * 创建新编辑器标签页
     * @param config 编辑器配置
     * @param text   文本
     */
    private static void createNewEditorTab(@NotNull final EditorConfig config, @NotNull final String text) {
        // 创建新的工具窗口标签页（该操作会触发UI更新）
        new MainToolWindowFactory().createNewTab(config.project(), config.toolWindow(), null);
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
     * @param project    当前项目实例
     * @param toolWindow JSON工具窗口实例
     */
    private record EditorConfig(Project project, ToolWindow toolWindow) {
    }
}