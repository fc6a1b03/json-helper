package com.acme.json.helper.ui.panel;

import com.acme.json.helper.common.Clipboard;
import com.acme.json.helper.core.parser.JsonNodeParser;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * JSON树面板
 *
 * @author 拒绝者
 * @date 2025-01-28
 */
public class JsonTreePanel extends JPanel {
    private final Tree jsonTree;
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");

    public JsonTreePanel() {
        super(new BorderLayout());
        this.jsonTree = new Tree();
        add(new JScrollPane(this.jsonTree));
    }

    /**
     * 加载JSON
     *
     * @param txt JSON文本
     */
    public void loadJson(final String txt) {
        jsonTree.setModel(new DefaultTreeModel(buildTreeModel(
                JsonNodeParser.parse("root", txt)
        )));
    }

    /**
     * 创建JSON树面板
     * @param editor 编辑
     * @return {@link JPanel }
     */
    public JPanel create(final EditorTextField editor) {
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(final @NotNull DocumentEvent e) {
                ApplicationManager.getApplication().invokeLater(() -> loadJson(editor.getText()));
            }
        });
        // 创建树面板
        final JPanel panel = new JPanel(new BorderLayout());
        // 配置树外观
        configureTreeAppearance();
        // 添加面板内容
        panel.add(new JBScrollPane(
                jsonTree,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        ), BorderLayout.CENTER);
        return panel;
    }

    /**
     * 构建树模型
     * @param node 节点
     * @return {@link DefaultMutableTreeNode }
     */
    private DefaultMutableTreeNode buildTreeModel(final JsonNodeParser.JsonNode node) {
        final DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        node.children().stream().map(this::buildTreeModel).forEach(treeNode::add);
        return treeNode;
    }

    /**
     * 配置树外观
     */
    private void configureTreeAppearance() {
        // 单击展开/折叠
        jsonTree.setToggleClickCount(1);
        // 隐藏默认根节点
        jsonTree.setRootVisible(Boolean.FALSE);
        jsonTree.setShowsRootHandles(Boolean.TRUE);
        // 配置默认UI
        jsonTree.setUI(new DefaultTreeUI());
        // 行高与间距调整
        jsonTree.setRowHeight(JBUI.scale(28));
        // 添加右键菜单
        addRightClickMenuToTree(jsonTree);
        // 自定义渲染器（使用平台图标体系）
        jsonTree.setCellRenderer(new TreeCellRenderer() {
            private final SimpleColoredComponent renderer = new SimpleColoredComponent();

            @Override
            public Component getTreeCellRendererComponent(final JTree tree, final Object value,
                                                          final boolean selected, final boolean expanded,
                                                          final boolean leaf, final int row, final boolean hasFocus) {
                renderer.clear();
                final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                // 背景色设置
                if (selected) {
                    renderer.setBackground(UIUtil.getTreeSelectionBackground(Boolean.TRUE));
                } else {
                    renderer.setBackground(UIUtil.getTreeBackground());
                }
                // 节点类型图标处理
                if (node.getUserObject() instanceof final JsonNodeParser.JsonNode data) {
                    // 使用平台内置图标体系
                    renderer.setIcon(
                            switch (data.type()) {
                                case "Object" -> AllIcons.Json.Object;
                                case "Array" -> AllIcons.Json.Array;
                                default -> AllIcons.Debugger.WatchLastReturnValue;
                            }
                    );
                    // 带颜色的文本渲染
                    renderer.append("%s: ".formatted(data.key()), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY));
                    renderer.append(String.valueOf(data.value()), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getTreeForeground()));
                }
                return renderer;
            }
        });
    }

    /**
     * 为树添加右键复制菜单
     *
     * @param tree 树
     */
    private void addRightClickMenuToTree(final Tree tree) {
        // 创建右键菜单
        final JPopupMenu popupMenu = new JPopupMenu();
        final JMenuItem copyItem = new JMenuItem(BUNDLE.getString("json.tree.copy"));
        // 复制动作实现
        copyItem.addActionListener(e -> {
            final TreePath path = tree.getSelectionPath();
            if (Objects.nonNull(path)) {
                Clipboard.copy(getNodeText(path.getLastPathComponent()));
            }
        });
        popupMenu.add(copyItem);
        // 添加鼠标监听器
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    // 选中右键点击的节点
                    final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    tree.setSelectionPath(path);
                    // 显示菜单
                    if (Objects.nonNull(path)) {
                        popupMenu.show(tree, e.getX(), e.getY());
                    }
                }
            }
        });
    }

    /**
     * 从树节点提取可复制的文本
     *
     * @param node node
     * @return {@link String }
     */
    private String getNodeText(final Object node) {
        if (node instanceof DefaultMutableTreeNode) {
            final Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
            if (Objects.nonNull(userObject)) {
                // 如果是键值对结构（例如 Map.Entry）
                if (userObject instanceof final Map.Entry<?, ?> entry) {
                    return String.format("%s: %s",
                            entry.getKey().toString(),
                            entry.getValue().toString()
                    );
                }
                return userObject.toString();
            }
        }
        return node.toString();
    }
}