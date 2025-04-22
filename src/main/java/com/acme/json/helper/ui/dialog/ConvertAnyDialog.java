package com.acme.json.helper.ui.dialog;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.parser.JsonParser;
import com.acme.json.helper.ui.editor.CustomizeEditorFactory;
import com.acme.json.helper.ui.editor.Editor;
import com.acme.json.helper.ui.editor.SupportedLanguages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * 转换各种文件对话框
 * @author 拒绝者
 * @date 2025-01-26
 */
public class ConvertAnyDialog extends DialogWrapper {
    /** 加载语言资源文件 */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /** 编辑器项目 */
    private final Project project;
    /**
     * 卡片面板
     */
    private final JPanel cardPanel;
    /**
     * 编辑器集合
     */
    private final Map<AnyFile, EditorTextField> editorMap = new EnumMap<>(AnyFile.class);

    public ConvertAnyDialog(final Project project, final String jsonText) {
        super(project, Boolean.TRUE);
        this.project = project;
        // 初始化创建所有编辑器
        Arrays.stream(AnyFile.values())
                .filter(fileType -> fileType != AnyFile.Text && fileType != AnyFile.JSON)
                .forEach(fileType -> this.editorMap.put(fileType, createEditorForType(fileType, jsonText)));
        this.cardPanel = new JPanel(new CardLayout());
        init();
    }

    @Override
    protected void init() {
        super.init();
        setModal(Boolean.FALSE);
        setResizable(Boolean.TRUE);
        setSize(800, 800);
        setTitle(BUNDLE.getString("dialog.convert.java.title"));
    }

    @Override
    protected JComponent createCenterPanel() {
        final JPanel mainPanel = new JPanel(new BorderLayout());
        final JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final ButtonGroup group = new ButtonGroup();
        // 添加卡片到面板
        editorMap.forEach((fileType, editor) -> cardPanel.add(new JBScrollPane(editor), fileType.name()));
        // 添加按钮到面板
        Arrays.stream(AnyFile.values())
                .filter(fileType -> fileType != AnyFile.Text && fileType != AnyFile.JSON)
                .forEach(fileType -> typePanel.add(createRadioButton(fileType, group)));
        // 设置默认显示
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, AnyFile.CLASS.name());
        mainPanel.add(typePanel, BorderLayout.NORTH);
        mainPanel.add(cardPanel, BorderLayout.CENTER);
        return mainPanel;
    }

    /**
     * 创建单选按钮
     *
     * @param fileType 文件类型
     * @param group 按钮组
     * @return {@link JRadioButton }
     */
    private JRadioButton createRadioButton(final AnyFile fileType, final ButtonGroup group) {
        final JRadioButton radio = new JRadioButton(StrUtil.toCamelCase(fileType.name().toLowerCase()));
        group.add(radio);
        if (fileType == AnyFile.CLASS) {
            radio.setSelected(Boolean.TRUE);
        }
        radio.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                ((CardLayout) cardPanel.getLayout()).show(cardPanel, fileType.name());
            }
        });
        return radio;
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
        editorMap.values().forEach(Container::removeAll);
        super.dispose();
    }

    /**
     * 为类型创建编辑器
     *
     * @param anyFile 文件类型
     * @param jsonText json文本
     * @return {@link EditorTextField }
     */
    private EditorTextField createEditorForType(final AnyFile anyFile, final String jsonText) {
        return Opt.ofNullable(
                        new CustomizeEditorFactory(SupportedLanguages.getByAnyFile(anyFile), "Dummy.%s".formatted(anyFile.extension()))
                                .create(project)
                )
                .peek(item -> item.setText(JsonParser.convert(jsonText, anyFile)))
                .peek(Editor::reformat).orElse(null);
    }
}