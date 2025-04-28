package com.acme.json.helper.ui.dialog;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.parser.JsonParser;
import com.acme.json.helper.ui.editor.CustomizeEditorFactory;
import com.acme.json.helper.ui.editor.Editor;
import com.acme.json.helper.ui.editor.enums.SupportedLanguages;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.util.*;
import java.util.stream.IntStream;

/**
 * 转换各种文件对话框
 * @author 拒绝者
 * @date 2025-01-26
 */
public class ConvertAnyDialog extends DialogWrapper {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 编辑器项目
     */
    private final Project project;
    /**
     * 卡片面板
     */
    private final JPanel cardPanel;
    /**
     * 表格集合
     */
    private final Map<AnyFile, JBTable> tableMap = new EnumMap<>(AnyFile.class);
    /**
     * 编辑器集合
     */
    private final Map<AnyFile, EditorTextField> editorMap = new EnumMap<>(AnyFile.class);

    public ConvertAnyDialog(final Project project, final String jsonText) {
        super(project, Boolean.TRUE);
        this.project = project;
        // 初始化创建所有编辑器及表格
        Arrays.stream(AnyFile.values())
                .filter(AnyFile::isEditor)
                .forEach(fileType -> this.editorMap.put(fileType, createEditorForType(fileType, jsonText)));
        Arrays.stream(AnyFile.values())
                .filter(AnyFile::isTable)
                .forEach(fileType -> this.tableMap.put(fileType, createTableForType(fileType, jsonText)));
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

    /**
     * 根据内容调整列
     * @param table 表格
     */
    private static void fitColumnsToContent(final JBTable table) {
        final JTableHeader header = table.getTableHeader();
        final int spacing = table.getIntercellSpacing().width;
        Collections.list(table.getColumnModel().getColumns()).forEach(column -> {
            final int colIdx = header.getColumnModel().getColumnIndex(column.getIdentifier());
            // 计算表头宽度 (不超过 200)
            final int headerWidth = Math.min(
                    header.getDefaultRenderer()
                            .getTableCellRendererComponent(table, column.getHeaderValue(), Boolean.FALSE, Boolean.FALSE, -1, colIdx)
                            .getPreferredSize().width, 200
            );
            // 计算数据最大宽度 (不超过 200)
            final int dataMaxWidth = IntStream.range(0, table.getRowCount())
                    .map(row -> Math.min(
                            table.getCellRenderer(row, colIdx)
                                    .getTableCellRendererComponent(table, table.getValueAt(row, colIdx), Boolean.FALSE, Boolean.FALSE, row, colIdx)
                                    .getPreferredSize().width, 200
                    )).max().orElse(headerWidth);
            // 最终列宽 = max(表头, 数据) + 间距
            column.setPreferredWidth(Math.max(headerWidth, dataMaxWidth) + spacing);
        });
        // 配置表格渲染器
        table.setDefaultRenderer(Object.class, getCenterRenderer());
    }

    /**
     * 获取中心渲染器
     * @return {@link DefaultTableCellRenderer }
     */
    private static DefaultTableCellRenderer getCenterRenderer() {
        final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        // 垂直居中
        centerRenderer.setVerticalAlignment(JLabel.CENTER);
        // 水平居中
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        return centerRenderer;
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

    @Override
    protected JComponent createCenterPanel() {
        final JPanel mainPanel = new JPanel(new BorderLayout());
        final JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final ButtonGroup group = new ButtonGroup();
        // 添加卡片到面板
        tableMap.forEach((fileType, table) -> cardPanel.add(new JBScrollPane(table), fileType.name()));
        editorMap.forEach((fileType, editor) -> cardPanel.add(new JBScrollPane(editor), fileType.name()));
        // 添加按钮到面板
        Arrays.stream(AnyFile.values())
                .filter(AnyFile::isRadio)
                .forEach(fileType -> typePanel.add(createRadioButton(fileType, group)));
        // 设置默认显示
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, AnyFile.CLASS.name());
        mainPanel.add(typePanel, BorderLayout.NORTH);
        mainPanel.add(cardPanel, BorderLayout.CENTER);
        return mainPanel;
    }

    /**
     * 创建单选按钮
     * @param fileType 文件类型
     * @param group    按钮组
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

    /**
     * 为类型创建编辑器
     * @param anyFile  文件类型
     * @param jsonText JSON文本
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

    /**
     * 为类型创建表格
     * @param anyFile  任何文件
     * @param jsonText JSON文本
     * @return {@link JBTable }
     */
    private JBTable createTableForType(final AnyFile anyFile, final String jsonText) {
        final JBTable table = new JBTable();
        // 关闭自动调整
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        // 生成表格数据
        Opt.ofBlankAble(JsonParser.convert(jsonText, anyFile))
                .map(JSON::parseObject)
                .ifPresentOrElse(item -> {
                    table.setModel(new DefaultTableModel(
                            JSON.parseObject(item.getString("data"), new TypeReference<>() {
                            }),
                            JSON.parseObject(item.getString("headers"), new TypeReference<Object[]>() {
                            })
                    ));
                    fitColumnsToContent(table);
                }, () -> table.setModel(new DefaultTableModel()));
        // 注册表格监听器
        table.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                fitColumnsToContent(table);
            }
        });
        return table;
    }
}