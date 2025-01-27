package com.acme.json.helper.ui.dialog;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.ObjectUtil;
import com.acme.json.helper.core.parser.JsonParser;
import com.acme.json.helper.ui.editor.Editor;
import com.acme.json.helper.ui.editor.JavaEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemListener;
import java.util.ResourceBundle;

/**
 * 转换JAVA对话框
 * @author 拒绝者
 * @date 2025-01-26
 */
public class ConvertJavaDialog extends DialogWrapper {
    /**
     * 语言类型
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    private final String jsonText;
    private final EditorTextField javaEditor;

    public ConvertJavaDialog(final Project project, final String jsonText) {
        super(project, Boolean.TRUE);
        this.jsonText = jsonText;
        this.javaEditor = new JavaEditor().create(project);
        init();
    }

    @Override
    protected void init() {
        // 初始化弹出层并设置参数
        super.init();
        setModal(Boolean.FALSE);
        setResizable(Boolean.TRUE);
        setSize(800, 600);
        setTitle(BUNDLE.getString("dialog.convert.java.title"));
        // 初始化编辑器内容
        SwingUtilities.invokeLater(() -> updateEditorContent(Boolean.TRUE));
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        // 主面板
        final JPanel mainPanel = new JPanel(new BorderLayout());
        // 类型选择面板
        final JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        // 单选框
        final ButtonGroup group = new ButtonGroup();
        final JRadioButton classRadioButton = new JRadioButton("class");
        final JRadioButton recordRadioButton = new JRadioButton("record");
        // 按钮组
        group.add(classRadioButton);
        group.add(recordRadioButton);
        // 设置默认选择
        classRadioButton.setSelected(Boolean.TRUE);
        // 添加事件监听
        final ItemListener radioListener = e -> SwingUtilities.invokeLater(() -> {
            // 等待编辑器初始化后，设置默认内容
            if (ObjectUtil.equal(e.getSource(), classRadioButton)) {
                updateEditorContent(Boolean.TRUE);
            } else if (ObjectUtil.equal(e.getSource(), recordRadioButton)) {
                updateEditorContent(Boolean.FALSE);
            }
        });
        classRadioButton.addItemListener(radioListener);
        recordRadioButton.addItemListener(radioListener);
        // 按钮增加到类型选择面板
        typePanel.add(classRadioButton);
        typePanel.add(recordRadioButton);
        // 编辑器面板
        mainPanel.add(typePanel, BorderLayout.NORTH);
        mainPanel.add(new JBScrollPane(javaEditor), BorderLayout.CENTER);
        return mainPanel;
    }

    @Override
    public void show() {
        super.show();
    }

    @Override
    protected JComponent createSouthPanel() {
        // 不显示底部按钮面板
        return null;
    }

    @Override
    protected Action @NotNull [] createActions() {
        // 移除所有默认按钮
        return new Action[0];
    }

    @Override
    public void dispose() {
        // 清空编辑器内容
        javaEditor.setText("");
        super.dispose();
    }

    /**
     * 更新编辑器内容
     * @param isClass 是阶级
     */
    private void updateEditorContent(final boolean isClass) {
        javaEditor.setText(
                Opt.of(isClass)
                        .filter(i -> i)
                        .map(item -> new JsonParser().convertToJsonClass(jsonText))
                        .orElseGet(() -> new JsonParser().convertToJsonRecord(jsonText))
        );
        Editor.reformat(javaEditor);
        javaEditor.setCaretPosition(0);
    }
}