package com.acme.prism.ui.dialog;

import cn.hutool.core.util.StrUtil;
import com.acme.prism.common.enums.AnyFile;
import com.acme.prism.core.json.JsonFormatter;
import com.acme.prism.core.notice.Notifier;
import com.acme.prism.core.parser.JsonParser;
import com.acme.prism.core.parser.converter.JavaStructure;
import com.alibaba.fastjson2.JSON;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
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
import com.intellij.util.concurrency.AppExecutorUtil;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * 创建类对话框
 * @author 拒绝者
 * @date 2025-05-06
 */
public class CreateClassDialog extends DialogWrapper {
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.PrismBundle");

    private final Project project;
    private final JBTextArea jsonTextArea;
    private final JBTextField classNameField;
    private final PsiDirectory targetDirectory;
    private final JBRadioButton classRadioButton;
    private final JBRadioButton recordRadioButton;
    private final Timer jsonFormatTimer;
    private final AtomicLong formatSequence = new AtomicLong();
    private final AtomicBoolean applyingJsonFormat = new AtomicBoolean(Boolean.FALSE);
    /**
     * JSON 自动格式化防抖延迟（毫秒）
     */
    private static final int FORMAT_DEBOUNCE_MS = 300;
    /**
     * 类名输入框列数
     */
    private static final int CLASS_NAME_FIELD_COLUMNS = 20;
    /**
     * JSON 输入区行数
     */
    private static final int JSON_AREA_ROWS = 10;
    /**
     * JSON 输入区列数
     */
    private static final int JSON_AREA_COLUMNS = 40;

    public CreateClassDialog(@Nullable final Project project, @NotNull final PsiDirectory targetDirectory) {
        super(project, Boolean.TRUE);
        this.project = project;
        this.targetDirectory = targetDirectory;
        this.classNameField = new JBTextField(CLASS_NAME_FIELD_COLUMNS);
        this.jsonTextArea = new JBTextArea(JSON_AREA_ROWS, JSON_AREA_COLUMNS);
        this.classRadioButton = new JBRadioButton(BUNDLE.getString("create.class.type.class"), Boolean.TRUE);
        this.recordRadioButton = new JBRadioButton(BUNDLE.getString("create.class.type.record"), Boolean.FALSE);
        this.jsonFormatTimer = new Timer(FORMAT_DEBOUNCE_MS, _ -> this.scheduleJsonFormatting());
        this.jsonFormatTimer.setRepeats(Boolean.FALSE);
        this.init();
        this.bindJsonFormattingListener();
    }

    @Override
    protected void init() {
        super.init();
        this.setModal(Boolean.FALSE);
        this.setResizable(Boolean.TRUE);
        this.setTitle(BUNDLE.getString("create.class.dialog.title"));
    }

    @Override
    public void dispose() {
        // 停止防抖定时器，避免对话框销毁后仍持有触发任务
        this.jsonFormatTimer.stop();
        super.dispose();
    }

    @Override
    protected @NotNull DialogStyle getStyle() {
        return DialogStyle.COMPACT;
    }

    @Override
    protected Action @NotNull [] createActions() {
        final Action okAction = new DialogWrapperAction(BUNDLE.getString("create.class.button.create")) {
            @Serial
            private static final long serialVersionUID = -4059918253684858372L;

            @Override
            protected void doAction(final ActionEvent e) {
                if (Objects.nonNull(CreateClassDialog.this.doValidate())) {
                    return;
                }
                final String className = CreateClassDialog.this.getClassName();
                final String jsonText = CreateClassDialog.this.getJsonText();
                final AnyFile targetType = CreateClassDialog.this.isRecord() ? AnyFile.RECORD : AnyFile.CLASS;
                new Task.Backgroundable(CreateClassDialog.this.project, BUNDLE.getString("create.class.progress.msg"), Boolean.FALSE) {
                    private String generatedClassText;

                    @Override
                    public void run(@NotNull final ProgressIndicator indicator) {
                        // 字面替换默认类名（replaceAll 的替换串中 $ 与 \ 会被当作特殊字符，存在崩溃风险）
                        this.generatedClassText = JsonParser.convert(jsonText, targetType)
                                .replace(JavaStructure.DEFAULT_CLASS_NAME, className);
                    }

                    @Override
                    public void onSuccess() {
                        WriteCommandAction.runWriteCommandAction(CreateClassDialog.this.project, () -> {
                            final PsiFile psiFile = CreateClassDialog.this.addGeneratedClassToFile(this.generatedClassText);
                            if (Objects.isNull(psiFile)) {
                                return;
                            }
                            CodeStyleManager.getInstance(CreateClassDialog.this.project).reformat(psiFile, Boolean.TRUE);
                            CreateClassDialog.this.close(OK_EXIT_CODE);
                            ApplicationManager.getApplication().invokeLater(() ->
                                    FileEditorManager.getInstance(CreateClassDialog.this.project).openFile(psiFile.getVirtualFile(), Boolean.TRUE)
                            );
                        });
                    }

                    @Override
                    public void onThrowable(@NotNull final Throwable error) {
                        super.onThrowable(error);
                        Notifier.notifyError(BUNDLE.getString("create.class.failed"), CreateClassDialog.this.project);
                    }
                }.queue();
            }
        };
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

    @Override
    protected @Nullable ValidationInfo doValidate() {
        // 校验转换后的类名（与生成逻辑取值口径一致，避免原始输入通过但转换后为空的边界）
        if (!this.isValidClassName(this.getClassName())) {
            return new ValidationInfo(BUNDLE.getString("create.class.name.invalid"), this.classNameField);
        }
        if (!JSON.isValid(this.jsonTextArea.getText())) {
            return new ValidationInfo(BUNDLE.getString("create.class.json.invalid"), this.jsonTextArea);
        }
        return null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        final JPanel dialogPanel = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        final ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(this.classRadioButton);
        typeGroup.add(this.recordRadioButton);

        final JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        radioPanel.add(this.classRadioButton);
        radioPanel.add(this.recordRadioButton);

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

    private PsiFile addGeneratedClassToFile(final String classText) {
        // 同名文件已存在时先清空其首个类（空文件无任何类声明，直接跳过避免数组越界）
        if (this.targetDirectory.findFile("%s.java".formatted(this.getClassName())) instanceof final PsiJavaFile file
                && file.getClasses().length > 0) {
            file.getClasses()[0].delete();
        }
        final PsiFile file = PsiFileFactory.getInstance(this.project)
                .createFileFromText("%s.java".formatted(this.getClassName()), JavaFileType.INSTANCE, classText, 0L, Boolean.FALSE, Boolean.FALSE);
        if (file instanceof final PsiJavaFile javaFile) {
            if (this.targetDirectory.add(javaFile) instanceof final PsiJavaFile javaFileElement) {
                return javaFileElement;
            }
        }
        return null;
    }

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

    public boolean isRecord() {
        return this.recordRadioButton.isSelected();
    }

    public String getClassName() {
        final String className = StrUtil.toCamelCase(this.classNameField.getText().trim());
        // 输入经驼峰转换后可能为空（如纯符号输入），需先判空再取首字符
        if (className.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(className.charAt(0)) + className.substring(1);
    }

    public String getJsonText() {
        return this.jsonTextArea.getText().trim();
    }

    private void bindJsonFormattingListener() {
        this.jsonTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(final DocumentEvent e) {
                CreateClassDialog.this.restartJsonFormatTimer();
            }

            @Override
            public void removeUpdate(final DocumentEvent e) {
                CreateClassDialog.this.restartJsonFormatTimer();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                CreateClassDialog.this.restartJsonFormatTimer();
            }
        });
    }

    private void restartJsonFormatTimer() {
        if (this.applyingJsonFormat.get()) {
            return;
        }
        if (this.jsonFormatTimer.isRunning()) {
            this.jsonFormatTimer.restart();
        } else {
            this.jsonFormatTimer.start();
        }
    }

    private void scheduleJsonFormatting() {
        final String rawText = this.jsonTextArea.getText();
        final long sequence = this.formatSequence.incrementAndGet();
        CompletableFuture
                .supplyAsync(() -> JSON.isValid(rawText) ? new JsonFormatter().process(rawText) : null, AppExecutorUtil.getAppExecutorService())
                .thenAccept(formattedJson -> ApplicationManager.getApplication().invokeLater(() -> {
                    if (Objects.isNull(formattedJson) || sequence != this.formatSequence.get()) {
                        return;
                    }
                    if (!StrUtil.equals(rawText, this.jsonTextArea.getText()) || StrUtil.equals(formattedJson, rawText)) {
                        return;
                    }
                    this.applyingJsonFormat.set(Boolean.TRUE);
                    try {
                        this.jsonTextArea.setText(formattedJson);
                    } finally {
                        this.applyingJsonFormat.set(Boolean.FALSE);
                    }
                }));
    }
}
