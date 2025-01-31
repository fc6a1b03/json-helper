package com.acme.json.helper.ui.panel;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
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

import javax.swing.Timer;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * JSON树面板
 *
 * @author 拒绝者
 * @date 2025-01-28
 */
public class JsonTreePanel extends JPanel {
    /** 加载语言资源文件 */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /** JSON树 */
    private final Tree jsonTree;
    /** 匹配集合 */
    private final List<TreePath> matches = new ArrayList<>();
    /** 搜索计时器 */
    private Timer searchTimer;
    /** 搜索文本 */
    private String searchText = "";
    /** 当前匹配指数 */
    private int currentMatchIndex = -1;
    /** 正在搜索 */
    private volatile boolean isSearching = Boolean.FALSE;

    public JsonTreePanel() {
        super(new BorderLayout());
        this.jsonTree = new Tree();
        add(new JScrollPane(this.jsonTree));
        // 配置`JsonTree`树外观
        configureTreeAppearance();
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
        // 设置可聚焦
        jsonTree.setFocusable(Boolean.TRUE);
        // 隐藏默认根节点
        jsonTree.setRootVisible(Boolean.FALSE);
        jsonTree.setShowsRootHandles(Boolean.TRUE);
        // 配置默认UI
        jsonTree.setUI(new DefaultTreeUI());
        // 行高与间距调整
        jsonTree.setRowHeight(JBUI.scale(28));
        // 添加右键菜单
        addRightClickMenuToTree(jsonTree);
        // 新增键盘监听和定时器初始化
        initSearchFeature();
        // 添加JSON树速度搜索的突出显示树单元渲染器
        jsonTree.setCellRenderer(new TreeCellRenderer() {
            private final SimpleColoredComponent renderer = new SimpleColoredComponent();

            @Override
            public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean selected,
                                                          final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
                renderer.clear();
                final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                // 背景色设置
                renderer.setBackground(selected ?
                        UIUtil.getTreeSelectionBackground(Boolean.TRUE) :
                        UIUtil.getTreeBackground()
                );
                // 处理不同节点类型
                switch (node.getUserObject()) {
                    case JsonNodeParser.JsonNode data -> renderJsonNode(data);
                    default -> renderer.append(node.toString());
                }
                return renderer;
            }

            /**
             * 渲染JSON节点
             *
             * @param data 数据
             */
            private void renderJsonNode(final JsonNodeParser.JsonNode data) {
                // 设置节点图标
                renderer.setIcon(switch (data.type()) {
                    case "Object" -> AllIcons.Json.Object;
                    case "Array" -> AllIcons.Json.Array;
                    default -> AllIcons.Debugger.WatchLastReturnValue;
                });
                // 构建显示文本
                final boolean isMatched = StrUtil.isNotEmpty(searchText) && "%s:%s".formatted(data.key(), data.value()).toLowerCase().contains(searchText);
                // 分割渲染键值部分
                renderer.append("%s:".formatted(data.key()),
                        isMatched ?
                                new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.BLUE) :
                                new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY)
                );
                renderer.append(String.valueOf(data.value()),
                        isMatched ?
                                new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.BLUE) :
                                new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getTreeForeground())
                );
            }
        });
    }

    /**
     * 为JSON树添加右键复制菜单
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

    /**
     * 初始化搜索功能相关组件
     */
    private void initSearchFeature() {
        // 初始化搜索定时器（延迟300毫秒执行搜索）
        searchTimer = new Timer(300, e -> {
            // 如正在搜索则停止当前搜索
            if (isSearching) return;
            // 重置搜索相关变量
            matches.clear();
            currentMatchIndex = -1;
            isSearching = Boolean.TRUE;
            // 执行搜索
            CompletableFuture.supplyAsync(() -> {
                final DefaultMutableTreeNode root = (DefaultMutableTreeNode) jsonTree.getModel().getRoot();
                collectMatches(root, new TreePath(root));
                return matches;
            }).thenAcceptAsync(result -> {
                matches.addAll(result);
                if (CollUtil.isNotEmpty(matches)) {
                    currentMatchIndex = 0;
                    scrollToMatch(currentMatchIndex);
                }
                jsonTree.repaint();
                isSearching = Boolean.FALSE;
            }, SwingUtilities::invokeLater);
        });
        searchTimer.setRepeats(Boolean.FALSE);
        // 添加键盘监听器
        jsonTree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(final KeyEvent e) {
                final char keyChar = e.getKeyChar();
                SwingUtilities.invokeLater(() -> {
                    // 退格键
                    if (keyChar == KeyEvent.VK_BACK_SPACE && StrUtil.isNotEmpty(searchText)) {
                        searchText = StrUtil.sub(searchText, 0, searchText.length() - 1);
                        restartSearchTimer();
                    }
                    // 退出键
                    else if (keyChar == KeyEvent.VK_ESCAPE) {
                        searchText = "";
                        matches.clear();
                        jsonTree.repaint();
                        currentMatchIndex = -1;
                    }
                    // 其他键
                    else if (Boolean.FALSE.equals(Character.isISOControl(keyChar))) {
                        searchText += Character.toLowerCase(keyChar);
                        restartSearchTimer();
                    }
                });
            }

            @Override
            public void keyPressed(final KeyEvent e) {
                final int keyCode = e.getKeyCode();
                if (keyCode == KeyEvent.VK_ENTER && CollUtil.isNotEmpty(matches)) {
                    currentMatchIndex = (currentMatchIndex + 1) % matches.size();
                    scrollToMatch(currentMatchIndex);
                } else if (keyCode == KeyEvent.VK_UP && CollUtil.isNotEmpty(matches)) {
                    currentMatchIndex = (currentMatchIndex - 1 + matches.size()) % matches.size();
                    scrollToMatch(currentMatchIndex);
                }
            }

            /**
             * 重启搜索定时器
             */
            private void restartSearchTimer() {
                if (searchTimer.isRunning()) {
                    searchTimer.restart();
                } else {
                    searchTimer.start();
                }
            }
        });
    }

    /**
     * 收集匹配的节点路径
     *
     * @param node node
     * @param path 路径
     */
    private void collectMatches(final DefaultMutableTreeNode node, final TreePath path) {
        // 判断节点是否匹配搜索条件
        if (switch (node.getUserObject()) {
            case JsonNodeParser.JsonNode jsonNode -> ("%s:%s".formatted(jsonNode.key(), jsonNode.value()))
                    .toLowerCase()
                    .contains(searchText);
            default -> node.toString().toLowerCase().contains(searchText);
        }) {
            matches.add(path);
        }
        // 判断节点子项是否匹配搜索条件
        final Enumeration<?> children = node.children();
        while (CollUtil.isNotEmpty(children) && children.hasMoreElements()) {
            final DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            collectMatches(child, path.pathByAddingChild(child));
        }
    }


    /**
     * 滚动到指定索引的匹配项
     *
     * @param index 指数
     */
    private void scrollToMatch(final int index) {
        final TreePath path = matches.get(index);
        jsonTree.expandPath(path.getParentPath());
        jsonTree.setSelectionPath(path);
        jsonTree.scrollPathToVisible(path);
    }
}