package com.acme.json.helper.ui.dialog;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.enums.AnyFile;
import com.acme.json.helper.core.parser.JsonParser;
import com.acme.json.helper.ui.editor.CustomizeEditorFactory;
import com.acme.json.helper.ui.editor.Editor;
import com.acme.json.helper.ui.editor.enums.SupportedLanguages;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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
                .forEach(fileType -> this.editorMap.put(fileType, this.createEditorForType(fileType, jsonText)));
        Arrays.stream(AnyFile.values())
                .filter(AnyFile::isTable)
                .forEach(fileType -> this.tableMap.put(fileType, this.createTableForType(fileType, jsonText)));
        this.cardPanel = new JPanel(new CardLayout(0, 0));
        this.cardPanel.setBorder(BorderFactory.createEmptyBorder());
        this.init();
    }

    @Override
    protected void init() {
        super.init();
        this.setModal(Boolean.FALSE);
        this.setResizable(Boolean.TRUE);
        this.setSize(800, 800);
        this.setTitle(BUNDLE.getString("dialog.convert.java.title"));
    }

    /**
     * 根据内容调整列
     * @param table 表格
     */
    private void fitColumnsToContent(final JBTable table) {
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
        this.editorMap.values().forEach(Container::removeAll);
        super.dispose();
    }

    @Override
    protected @NotNull DialogStyle getStyle() {
        return DialogStyle.COMPACT;
    }

    @Override
    protected JComponent createCenterPanel() {
        final ButtonGroup group = new ButtonGroup();
        final JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        final JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        // 添加卡片到面板
        this.tableMap.forEach((fileType, table) -> {
            final JPanel panel = new JPanel(new BorderLayout(0, 0));
            panel.setBorder(BorderFactory.createEmptyBorder());
            panel.add(this.createButton(table), BorderLayout.NORTH);
            panel.add(new JBScrollPane(table), BorderLayout.CENTER);
            this.cardPanel.add(panel, fileType.name());
        });
        this.editorMap.forEach((fileType, editor) -> {
            final JBScrollPane pane = new JBScrollPane(editor);
            pane.setBorder(BorderFactory.createEmptyBorder());
            this.cardPanel.add(pane, fileType.name());
        });
        // 添加按钮到面板
        Arrays.stream(AnyFile.values())
                .filter(AnyFile::isRadio)
                .forEach(fileType -> typePanel.add(this.createRadioButton(fileType, group)));
        // 设置默认显示
        ((CardLayout) this.cardPanel.getLayout()).show(this.cardPanel, AnyFile.CLASS.name());
        mainPanel.add(typePanel, BorderLayout.NORTH);
        mainPanel.add(this.cardPanel, BorderLayout.CENTER);
        return mainPanel;
    }

    /**
     * 创建标准化按钮
     * @param table 表格
     * @return 配置好的JButton实例
     */
    private JButton createButton(final JBTable table) {
        final JButton button = new JButton();
        button.setEnabled(Boolean.TRUE);
        button.setIcon(AllIcons.General.Export);
        button.addActionListener(e -> this.exportXlsx(table));
        button.setPreferredSize(new Dimension(35, 35));
        return button;
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
                ((CardLayout) this.cardPanel.getLayout()).show(this.cardPanel, fileType.name());
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
                                .create(this.project)
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
        // 取消边框
        table.setBorder(BorderFactory.createEmptyBorder());
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
                    this.fitColumnsToContent(table);
                }, () -> table.setModel(new DefaultTableModel()));
        // 注册表格监听器
        table.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                ConvertAnyDialog.this.fitColumnsToContent(table);
            }
        });
        return table;
    }

    /**
     * 导出xlsx
     * @param table 表格
     */
    private void exportXlsx(final JTable table) {
        // 目录选择器
        final VirtualFile selectedDirectory = FileChooser.chooseFile(
                new FileChooserDescriptor(Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE),
                this.project, null
        );
        if (Objects.nonNull(selectedDirectory)) {
            // 设置文件路径
            final String filePath = Paths.get(selectedDirectory.getPath(), "export_%s.xlsx".formatted(DatePattern.PURE_DATETIME_FORMATTER.format(LocalDateTime.now()))).toString();
            try (final Workbook workbook = new XSSFWorkbook()) {
                final Sheet sheet = workbook.createSheet("Data");
                // 单元格样式
                final CellStyle cellStyle = workbook.createCellStyle();
                cellStyle.setAlignment(HorizontalAlignment.CENTER);
                // 写入表头
                final Row headerRow = sheet.createRow(0);
                IntStream.range(0, table.getColumnCount()).forEach(colIdx -> {
                    final Cell cell = headerRow.createCell(colIdx);
                    cell.setCellStyle(cellStyle);
                    cell.setCellValue(table.getColumnName(colIdx));
                });
                // 写入数据行
                IntStream.range(0, table.getRowCount()).forEach(rowIdx -> {
                    final Row row = sheet.createRow(rowIdx + 1);
                    IntStream.range(0, table.getColumnCount()).forEach(colIdx -> {
                        final Cell cell = row.createCell(colIdx);
                        final Object value = table.getValueAt(rowIdx, colIdx);
                        if (value instanceof Number) {
                            cell.setCellValue(((Number) value).doubleValue());
                        } else {
                            cell.setCellValue(value.toString());
                        }
                    });
                });
                // 自动调整列宽
                IntStream.range(0, table.getColumnCount()).forEach(sheet::autoSizeColumn);
                // 写出文件
                try (final FileOutputStream fileOut = new FileOutputStream(filePath)) {
                    workbook.write(fileOut);
                }
            } catch (final IOException ignored) {
            }
        }
    }
}