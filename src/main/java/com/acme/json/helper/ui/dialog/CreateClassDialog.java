package com.acme.json.helper.ui.dialog;

import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.json.JsonFormatter;
import com.acme.json.helper.core.parser.JsonParser;
import com.alibaba.fastjson2.JSON;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.Serial;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.IntStream;

/**
 * 创建类对话框
 * @author 拒绝者
 * @date 2025-05-06
 */
public class CreateClassDialog extends DialogWrapper {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 项目
     */
    private final Project project;
    /**
     * json文本区域
     */
    private final JBTextArea jsonTextArea;
    /**
     * 类名字段
     */
    private final JBTextField classNameField;
    /**
     * 目标目录
     */
    private final PsiDirectory targetDirectory;
    /**
     * 实体类单选按钮
     */
    private final JBRadioButton classRadioButton;
    /**
     * 记录类单选按钮
     */
    private final JBRadioButton recordRadioButton;

    /**
     * 创建类对话框
     * @param project 项目
     */
    public CreateClassDialog(@Nullable final Project project, @NotNull final PsiDirectory targetDirectory) {
        super(project, Boolean.TRUE);
        // 基础信息
        this.project = project;
        this.targetDirectory = targetDirectory;
        // 创建基础组件
        this.classNameField = new JBTextField(20);
        this.jsonTextArea = new JBTextArea(10, 40);
        this.classRadioButton = new JBRadioButton("Class", Boolean.TRUE);
        this.recordRadioButton = new JBRadioButton("Record", Boolean.FALSE);
        // 初始化面板
        this.init();
        // JSON文本变更监听
        this.jsonTextArea.getDocument().addDocumentListener(new DocumentListener() {
            /**
             * 更新JSON
             */
            private void updateJson() {
                if (JSON.isValid(CreateClassDialog.this.jsonTextArea.getText())) {
                    final String formattedJson = new JsonFormatter().process(CreateClassDialog.this.jsonTextArea.getText());
                    if (!StrUtil.equals(formattedJson, CreateClassDialog.this.jsonTextArea.getText())) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            if (JSON.isValid(CreateClassDialog.this.jsonTextArea.getText())) {
                                CreateClassDialog.this.jsonTextArea.setText(formattedJson);
                            }
                        });
                    }
                }
            }

            @Override
            public void insertUpdate(final DocumentEvent e) {
                this.updateJson();
            }

            @Override
            public void removeUpdate(final DocumentEvent e) {
                this.updateJson();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                this.updateJson();
            }
        });
    }

    @Override
    protected void init() {
        super.init();
        this.setModal(Boolean.FALSE);
        this.setResizable(Boolean.TRUE);
        this.setTitle(BUNDLE.getString("create.class.dialog.title"));
    }

    @Override
    protected @NotNull DialogStyle getStyle() {
        return DialogStyle.COMPACT;
    }

    @Override
    protected Action @NotNull [] createActions() {
        // 自定义确认按钮
        final Action okAction = new DialogWrapperAction(BUNDLE.getString("create.class.button.create")) {
            @Serial
            private static final long serialVersionUID = -4059918253684858372L;

            @Override
            protected void doAction(final ActionEvent e) {
                if (Objects.isNull(CreateClassDialog.this.doValidate())) {
                    WriteCommandAction.runWriteCommandAction(CreateClassDialog.this.project, () -> {
                        // 创建文件
                        final PsiFile psiFile = CreateClassDialog.this.addGeneratedClassToFile(
                                JsonParser.convert(
                                        CreateClassDialog.this.getJsonText(),
                                        CreateClassDialog.this.isRecord() ? AnyFile.RECORD : AnyFile.CLASS
                                ).replaceAll("Dummy", CreateClassDialog.this.getClassName())
                        );
                        if (Objects.nonNull(psiFile)) {
                            // 关闭窗口
                            CreateClassDialog.this.close(OK_EXIT_CODE);
                            // 格式化文件
                            CodeStyleManager.getInstance(CreateClassDialog.this.project).reformat(psiFile, Boolean.TRUE);
                            // 打开文件
                            ApplicationManager.getApplication().invokeLater(() ->
                                    FileEditorManager.getInstance(CreateClassDialog.this.project).openFile(psiFile.getVirtualFile(), Boolean.TRUE)
                            );
                        }
                    });
                }
            }
        };
        // 自定义关闭按钮
        final Action closeAction = new DialogWrapperAction(BUNDLE.getString("create.class.button.close")) {
            @Serial
            private static final long serialVersionUID = 5042304239468196399L;

            @Override
            protected void doAction(final ActionEvent e) {
                CreateClassDialog.this.close(CANCEL_EXIT_CODE);
            }
        };
        return new Action[]{okAction, closeAction};
    }

    /**
     * 验证
     * @return {@link ValidationInfo }
     */
    @Override
    protected @Nullable ValidationInfo doValidate() {
        // 类名验证
        if (!this.isValidClassName(this.classNameField.getText())) {
            return new ValidationInfo(BUNDLE.getString("create.class.name.invalid"), this.classNameField);
        }
        // JSON验证
        if (!JSON.isValid(this.jsonTextArea.getText())) {
            return new ValidationInfo(BUNDLE.getString("create.class.json.invalid"), this.jsonTextArea);
        }
        return null;
    }

    /**
     * 创建中心面板
     * @return {@link JComponent }
     */
    @Override
    protected @Nullable JComponent createCenterPanel() {
        final JPanel dialogPanel = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        // 单选按钮组
        final ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(this.classRadioButton);
        typeGroup.add(this.recordRadioButton);
        final JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        radioPanel.add(this.classRadioButton);
        radioPanel.add(this.recordRadioButton);
        // 布局组件
        gbc.gridx = 0;
        gbc.gridy = 0;
        dialogPanel.add(new JLabel(BUNDLE.getString("create.class.type.label")), gbc);
        gbc.gridx = 1;
        dialogPanel.add(radioPanel, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        dialogPanel.add(new JLabel(BUNDLE.getString("create.class.name.label")), gbc);
        gbc.gridx = 1;
        dialogPanel.add(this.classNameField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        dialogPanel.add(new JLabel(BUNDLE.getString("create.class.json.label")), gbc);
        gbc.gridx = 1;
        dialogPanel.add(new JScrollPane(this.jsonTextArea), gbc);
        return dialogPanel;
    }

    /**
     * 将生成的类添加到文件
     * @param classText 类内容
     * @return PsiFile 生成的文件
     */
    private PsiFile addGeneratedClassToFile(final String classText) {
        // 文件存在则删除旧文件
        if (this.targetDirectory.findFile("%s.java".formatted(this.getClassName())) instanceof final PsiJavaFile file) {
            file.getClasses()[0].delete();
        }
        // 创建文件
        final PsiFile file = PsiFileFactory.getInstance(this.project)
                .createFileFromText("%s.java".formatted(this.getClassName()), JavaFileType.INSTANCE, classText, 0L, Boolean.FALSE, Boolean.FALSE);
        // 确认类型并写入目录
        if (file instanceof final PsiJavaFile javaFile) {
            if (this.targetDirectory.add(javaFile) instanceof final PsiJavaFile javaFileElement) {
                return javaFileElement;
            }
        }
        return null;
    }

    /**
     * 是效类名
     * @param className 类名
     * @return boolean
     */
    private boolean isValidClassName(@NotNull final String className) {
        if (StrUtil.isEmpty(className)) {
            return Boolean.FALSE;
        }
        final char[] chars = className.toCharArray();
        return Character.isJavaIdentifierStart(chars[0]) &&
                IntStream.range(1, chars.length)
                        .mapToObj(i -> chars[i])
                        .allMatch(Character::isJavaIdentifierPart);
    }

    /**
     * 是记录类
     * @return boolean
     */
    public boolean isRecord() {
        return this.recordRadioButton.isSelected();
    }

    /**
     * 获取类名
     * @return {@link String }
     */
    public String getClassName() {
        final String className = StrUtil.toCamelCase(this.classNameField.getText().trim());
        return Character.toUpperCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * 获取JSON文本
     * @return {@link String }
     */
    public String getJsonText() {
        return this.jsonTextArea.getText().trim();
    }
}