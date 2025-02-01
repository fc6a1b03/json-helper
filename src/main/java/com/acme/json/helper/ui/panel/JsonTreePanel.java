package com.acme.json.helper.ui.panel;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.acme.json.helper.common.Clipboard;
import com.acme.json.helper.core.parser.JsonNodeParser;
import com.alibaba.fastjson2.JSON;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    /** 搜索输入展示框 */
    private JTextField searchField;
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
    /** 数组索引模式 */
    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("^\\[(\\d+)]$");

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
        // 添加搜索框内容
        panel.add(createSearchField(), BorderLayout.NORTH);
        return panel;
    }

    /**
     * 创建搜索框
     *
     * @return {@link JTextField }
     */
    private JTextField createSearchField() {
        searchField = new JTextField();
        // 搜索框只读
        searchField.setEditable(Boolean.FALSE);
        // 搜索框样式
        searchField.setForeground(JBColor.GRAY);
        searchField.setBackground(UIUtil.getPanelBackground());
        searchField.setBorder(BorderFactory.createCompoundBorder(
                // 底部边框
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                // 内边距
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));
        searchField.setFont(UIUtil.getLabelFont().deriveFont(Font.PLAIN));
        searchField.setPreferredSize(new Dimension(Short.MAX_VALUE, 20));
        return searchField;
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
        // 复制对象
        final JMenuItem copyItem = new JMenuItem(BUNDLE.getString("json.tree.copy"));
        copyItem.addActionListener(e ->
                Opt.ofNullable(tree.getSelectionPath())
                        .ifPresent(path -> Clipboard.copy(getNodeText(path.getLastPathComponent(), 1)))
        );
        // 复制路径
        final JMenuItem copyPath = new JMenuItem(BUNDLE.getString("json.tree.copy.path"));
        copyPath.addActionListener(e ->
                Opt.ofNullable(tree.getSelectionPath())
                        .ifPresent(path -> Clipboard.copy(buildJsonPath(path)))
        );
        // 复制键
        final JMenuItem copyKey = new JMenuItem(BUNDLE.getString("json.tree.copy.key"));
        copyKey.addActionListener(e ->
                Opt.ofNullable(tree.getSelectionPath())
                        .ifPresent(path -> Clipboard.copy(getNodeText(path.getLastPathComponent(), 2)))
        );
        // 复制值
        final JMenuItem copyVal = new JMenuItem(BUNDLE.getString("json.tree.copy.val"));
        copyVal.addActionListener(e ->
                Opt.ofNullable(tree.getSelectionPath())
                        .ifPresent(path -> Clipboard.copy(getNodeText(path.getLastPathComponent(), 3)))
        );
        popupMenu.add(copyKey);
        popupMenu.add(copyVal);
        popupMenu.add(copyPath);
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
     * @param node 选择的节点
     * @param type 类型：1.object 2.key 3.value
     * @return {@link String }
     */
    private String getNodeText(final Object node, final Integer type) {
        return switch (node) {
            // 确认是`DefaultMutableTreeNode`才会处理节点
            case final DefaultMutableTreeNode treeNode -> Opt.ofNullable(treeNode.getUserObject())
                    .map(Object::toString)
                    .filter(StrUtil::isNotEmpty)
                    .map(item -> switch (type) {
                        // 对象
                        case Integer i when ObjectUtil.equal(i, 1) -> item;
                        // 键
                        case Integer i when ObjectUtil.equal(i, 2) -> Opt.of(JSON.isValidObject(item))
                                .filter(b -> b)
                                .map(b -> JSON.parseObject(item).keySet().stream().filter(StrUtil::isNotEmpty).findFirst().orElse(null))
                                .orElse(null);
                        // 值
                        case Integer i when ObjectUtil.equal(i, 3) -> Opt.of(JSON.isValidObject(item))
                                .filter(b -> b)
                                .map(b -> JSON.parseObject(item).values().stream().filter(Objects::nonNull).findFirst().orElse(null))
                                .orElse(null);
                        default -> null;
                    })
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(StrUtil::isNotEmpty)
                    .orElse(node.toString());
            case null, default -> Convert.toStr(node);
        };
    }

    /**
     * 构建JSON路径
     *
     * @param selectionPath 选择的路径
     * @return {@link String }
     */
    private String buildJsonPath(final TreePath selectionPath) {
        // 从根节点之后开始遍历
        return IntStream.range(1, selectionPath.getPathCount())
                // 获取每个路径组件
                .mapToObj(selectionPath::getPathComponent)
                // 确保是`DefaultMutableTreeNode`类型
                .filter(DefaultMutableTreeNode.class::isInstance)
                // 将对象转换为`DefaultMutableTreeNode`类型
                .map(DefaultMutableTreeNode.class::cast)
                // 获取用户选择对象
                .map(DefaultMutableTreeNode::getUserObject)
                // 确保是`JsonNodeParser.JsonNode`类型
                .filter(JsonNodeParser.JsonNode.class::isInstance)
                // 将对象转换为`JsonNodeParser.JsonNode`类型
                .map(JsonNodeParser.JsonNode.class::cast)
                // 获取键
                .map(JsonNodeParser.JsonNode::key)
                // 过滤掉空的键字符串
                .filter(StrUtil::isNotEmpty)
                // 组合各节点的路径表达式
                .map(key ->
                        // 匹配数组索引模式
                        Opt.ofNullable(ARRAY_INDEX_PATTERN.matcher(key))
                                // 检查是否匹配成功
                                .filter(Matcher::matches)
                                // 格式化数组索引
                                .map(item -> "[%d]".formatted(Convert.toInt(item.group(1))))
                                // 格式化普通键值
                                .orElseGet(() -> ".%s".formatted(key))
                )
                // 将所有部分连接成最终的 JSON 路径字符串
                .collect(Collectors.joining("", "$", ""));
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
                ApplicationManager.getApplication().invokeLater(() -> {
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
                    updateSearchField();
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

            /**
             * 更新搜索字段
             */
            private void updateSearchField() {
                if (StrUtil.isNotEmpty(searchText)) {
                    searchField.setText(searchText);
                } else {
                    searchField.setText("");
                }
                searchField.setForeground(UIUtil.getTreeForeground());
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