package com.acme.json.helper.ui.dialog;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.convert.Convert;
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
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
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
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
     * 原始JSON
     */
    private final String jsonText;
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
        this.jsonText = jsonText;
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
     * 懒加载
     * @param fileType 文件类型
     */
    private void lazyLoad(final AnyFile fileType) {
        if (Objects.isNull(fileType)) {
            return;
        }
        // 加载 编辑器
        if (fileType.isEditor() && !this.editorMap.containsKey(fileType)) {
            new Task.Backgroundable(this.project, BUNDLE.getString("json.to.any.load.content.editor").formatted(fileType)) {
                @Override
                public void run(@NotNull final ProgressIndicator indicator) {
                    final EditorTextField editor = ConvertAnyDialog.this.createEditorForType(fileType, ConvertAnyDialog.this.jsonText);
                    SwingUtilities.invokeLater(() -> {
                        // 删掉旧占位
                        ConvertAnyDialog.this.removePlaceholder();
                        // 放置新组件
                        ConvertAnyDialog.this.editorMap.put(fileType, editor);
                        ConvertAnyDialog.this.cardPanel.add(new JBScrollPane(editor), fileType.name());
                        ((CardLayout) ConvertAnyDialog.this.cardPanel.getLayout()).show(ConvertAnyDialog.this.cardPanel, fileType.name());
                        // 重新加载
                        ConvertAnyDialog.this.cardPanel.revalidate();
                    });
                }
            }.queue();
        }
        // 加载 表格
        else if (fileType.isTable() && !this.tableMap.containsKey(fileType)) {
            new Task.Backgroundable(this.project, BUNDLE.getString("json.to.any.load.content.table").formatted(fileType)) {
                @Override
                public void run(@NotNull final ProgressIndicator indicator) {
                    final JBTable table = ConvertAnyDialog.this.createTableForType(fileType, ConvertAnyDialog.this.jsonText);
                    SwingUtilities.invokeLater(() -> {
                        ConvertAnyDialog.this.tableMap.put(fileType, table);
                        final JPanel panel = new JPanel(new BorderLayout());
                        panel.add(ConvertAnyDialog.this.createButton(table), BorderLayout.NORTH);
                        panel.add(new JBScrollPane(table), BorderLayout.CENTER);
                        ConvertAnyDialog.this.cardPanel.add(panel, fileType.name());
                        ((CardLayout) ConvertAnyDialog.this.cardPanel.getLayout()).show(ConvertAnyDialog.this.cardPanel, fileType.name());
                    });
                }
            }.queue();
        }
        // 已加载过，直接切换
        else {
            ((CardLayout) this.cardPanel.getLayout()).show(this.cardPanel, fileType.name());
        }
    }

    /**
     * 删掉占位 JLabel
     */
    private void removePlaceholder() {
        Arrays.stream(this.cardPanel.getComponents()).filter(JLabel.class::isInstance).findFirst().ifPresent(this.cardPanel::remove);
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
        // 占位加载组件
        this.cardPanel.add(new JLabel(BUNDLE.getString("json.to.any.load"), SwingConstants.CENTER), AnyFile.CLASS.name());
        // 添加 按钮到面板
        Arrays.stream(AnyFile.values()).filter(AnyFile::isRadio)
                .forEach(fileType -> typePanel.add(this.createRadioButton(fileType, group)));
        // 设置默认显示
        ((CardLayout) this.cardPanel.getLayout()).show(this.cardPanel, AnyFile.CLASS.name());
        // 设置默认加载
        this.lazyLoad(AnyFile.CLASS);
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
                this.lazyLoad(fileType);
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
        // JSON内容转换
        final AtomicReference<String> converted = new AtomicReference<>();
        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            converted.set(executor.submit(() -> JsonParser.convert(jsonText, anyFile)).get());
        } catch (final Exception ignored) {
            converted.set("");
        }
        // 加载并填充 编辑器
        final List<EditorTextField> holder = ListUtil.toList();
        ApplicationManager.getApplication().invokeAndWait(() -> holder.add(this.buildEditor(anyFile, converted.get())), ModalityState.any());
        return holder.getFirst();
    }

    /**
     * 构建编辑器
     * @param anyFile   任何文件
     * @param converted 转换
     * @return {@link EditorTextField }
     */
    private EditorTextField buildEditor(final AnyFile anyFile, final String converted) {
        final EditorTextField field = new CustomizeEditorFactory(SupportedLanguages.getByAnyFile(anyFile), "Dummy.%s".formatted(anyFile.extension()))
                .create(this.project);
        field.setText(converted);
        Editor.reformat(field);
        return field;
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
            final String filePath = Convert.toStr(Paths.get(selectedDirectory.getPath(), "export_%s.xlsx".formatted(DatePattern.PURE_DATETIME_FORMATTER.format(LocalDateTime.now()))));
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
                            cell.setCellValue(Convert.toDouble(value));
                        } else {
                            cell.setCellValue(Convert.toStr(value));
                        }
                    });
                });
                // 自动调整列宽
                IntStream.range(0, table.getColumnCount()).forEach(sheet::autoSizeColumn);
                // 写出文件
                try (final FileOutputStream fileOut = new FileOutputStream(filePath)) {
                    workbook.write(fileOut);
                }
            } catch (final Exception e) {
                Messages.showErrorDialog("%s: %s".formatted(BUNDLE.getString("export.xlsx.error.msg"), e.getMessage()), BUNDLE.getString("export.xlsx.error.title"));
            }
        }
    }
}