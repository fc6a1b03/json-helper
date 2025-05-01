package com.acme.json.helper.ui.action;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.parser.JsonParser;
import com.alibaba.fastjson2.JSON;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.IntStream;

/**
 * 从JSON创建类
 *
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
        e.getPresentation().setEnabledAndVisible(Objects.nonNull(e.getProject()) && Objects.nonNull(this.getPsiDirectory(e.getDataContext().getData(CommonDataKeys.PSI_ELEMENT))));
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
        Opt.ofNullable(e.getProject())
                .ifPresent(project -> {
                    // 当前目录
                    final PsiDirectory targetDirectory = this.getPsiDirectory(e.getDataContext().getData(CommonDataKeys.PSI_ELEMENT));
                    if (Objects.isNull(targetDirectory)) return;
                    // 类名
                    final String className = this.readName(project);
                    if (StrUtil.isEmpty(className)) return;
                    // JSON内容
                    final String json = this.readJson(project);
                    if (StrUtil.isEmpty(json)) return;
                    // 是否为记录类
                    final boolean isRecord = Messages.YES == Messages.showYesNoDialog(project, BUNDLE.getString("create.class.is.record.message"), BUNDLE.getString("create.class.is.record.title"), Messages.getQuestionIcon());
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        // 创建文件
                        Opt.ofNullable(this.addGeneratedClassToFile(project, targetDirectory, className, JsonParser.convert(json, isRecord ? AnyFile.RECORD : AnyFile.CLASS).replaceAll("Dummy", className)))
                                // 触发文件格式化
                                .ifPresent(file -> ApplicationManager.getApplication().invokeLater(() -> CodeStyleManager.getInstance(project).reformat(file)));
                    });
                });
    }

    /**
     * 获取PSI目录
     *
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

    /**
     * 读取名称
     *
     * @param project 项目
     * @return {@link String }
     */
    private String readName(final Project project) {
        return Opt.ofBlankAble(Messages.showInputDialog(project, BUNDLE.getString("create.class.name.message"), BUNDLE.getString("create.class.name.title"), Messages.getQuestionIcon(), "", new InputValidator() {
                    @Override
                    public boolean checkInput(final String inputString) {
                        return CreateClassFromJsonAction.this.isValidClassName(inputString);
                    }

                    @Override
                    public boolean canClose(final String inputString) {
                        return Boolean.TRUE;
                    }
                }))
                .map(String::trim)
                .map(StrUtil::toCamelCase)
                .map(item -> Character.toUpperCase(item.charAt(0)) + item.substring(1))
                .orElse("");
    }

    /**
     * 是有效类名
     *
     * @param className 类名
     * @return boolean
     */
    private boolean isValidClassName(final String className) {
        if (StrUtil.isEmpty(className)) {
            return Boolean.FALSE;
        }
        final char[] chars = className.toCharArray();
        if (!Character.isJavaIdentifierStart(chars[0])) {
            return Boolean.FALSE;
        }
        return IntStream.range(1, chars.length).allMatch(i -> Character.isJavaIdentifierPart(chars[i]));
    }

    /**
     * 读取JSON
     *
     * @param project 项目
     * @return {@link String }
     */
    private String readJson(final Project project) {
        return Opt.ofBlankAble(Messages.showMultilineInputDialog(project, BUNDLE.getString("create.class.json.message"), BUNDLE.getString("create.class.json.title"), "", Messages.getQuestionIcon(), new InputValidator() {
            @Override
            public boolean checkInput(final String inputString) {
                return JSON.isValid(inputString);
            }

            @Override
            public boolean canClose(final String inputString) {
                return Boolean.TRUE;
            }
        })).map(String::trim).orElse("");
    }

    /**
     * 将生成类添加到文件
     *
     * @param project 项目
     * @param directory 目录
     * @param className 类名
     * @param classText 类文本
     * @return {@link PsiFile }
     */
    private PsiFile addGeneratedClassToFile(final Project project, final PsiDirectory directory, final String className, final String classText) {
        // 文件存在则删除旧文件
        if (directory.findFile("%s.java".formatted(className)) instanceof final PsiJavaFile javaFile) {
            javaFile.getClasses()[0].delete();
        }
        // 创建文件
        final PsiFile javaFile = PsiFileFactory.getInstance(project).createFileFromText("%s.java".formatted(className), JavaFileType.INSTANCE, classText);
        // 加入当前目录
        directory.add(javaFile);
        // 刷新Psi树以确保文档关联
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        // 返回文件对象
        return javaFile;
    }
}