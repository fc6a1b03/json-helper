package com.acme.json.helper.core.settings;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import static com.acme.json.helper.ui.MainToolWindowFactory.PROJECT_NAME;

/**
 * 插件设置
 * @author 拒绝者
 * @date 2025-02-02
 */
public class PluginSettings implements Configurable {
    /**
     * 加载语言资源文件
     */
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.JsonHelperBundle");
    /**
     * 设置组件
     */
    private PluginSettingsComponent component;

    /**
     * 获取设置
     * @return {@link PluginSettingsState }
     */
    public static PluginSettingsState of() {
        return PluginSettingsState.getInstance();
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return PROJECT_NAME;
    }

    @NotNull
    @Override
    public JComponent getPreferredFocusedComponent() {
        return component.getPreferredFocusedComponent();
    }

    @Override
    public @Nullable JComponent createComponent() {
        return new PluginSettingsComponent().getPanel(component -> this.component = component);
    }

    @Override
    public boolean isModified() {
        // 获取设置状态
        final PluginSettingsState settings = of();
        // 判断全局状态与设置组件状态
        return component.getCopyJson() != settings.copyJson
                || component.getJsonHelper() != settings.jsonHelper
                || component.getPortSearch() != settings.portSearchEnabled
                || component.getProjectSearch() != settings.projectSearchEnabled
                || component.getHttpSearch() != settings.httpSearchEnabled
                || component.getArchiveNode() != settings.archiveNodeEnabled
                || component.getRainbowBracketPair() != settings.rainbowBracketPairEnabled;
    }

    @Override
    public void apply() {
        // 获取设置状态
        final PluginSettingsState settings = of();
        // 记录压缩包节点开关旧值（用于变更后刷新项目树）
        final boolean previousArchiveNodeEnabled = settings.archiveNodeEnabled;
        // 将设置组件状态覆盖全局状态
        settings.copyJson = component.getCopyJson();
        settings.jsonHelper = component.getJsonHelper();
        settings.portSearchEnabled = component.getPortSearch();
        settings.projectSearchEnabled = component.getProjectSearch();
        settings.httpSearchEnabled = component.getHttpSearch();
        settings.archiveNodeEnabled = component.getArchiveNode();
        settings.rainbowBracketPairEnabled = component.getRainbowBracketPair();
        // 压缩包节点开关变更后刷新全部打开项目的项目树（平台对 TreeStructureProvider 结果有节点缓存，需主动刷新）
        if (previousArchiveNodeEnabled != settings.archiveNodeEnabled) {
            for (final Project openProject : ProjectManager.getInstance().getOpenProjects()) {
                ProjectView.getInstance(openProject).refresh();
            }
        }
    }

    @Override
    public void reset() {
        final PluginSettingsState settings = of();
        component.setCopyJson(settings.copyJson);
        component.setJsonHelper(settings.jsonHelper);
        component.setPortSearch(settings.portSearchEnabled);
        component.setProjectSearch(settings.projectSearchEnabled);
        component.setHttpSearch(settings.httpSearchEnabled);
        component.setArchiveNode(settings.archiveNodeEnabled);
        component.setRainbowBracketPair(settings.rainbowBracketPairEnabled);
    }

    @Override
    public void disposeUIResources() {
        component = null;
    }


    /**
     * 设置组件详细
     * @author 拒绝者
     * @date 2025-02-02
     */
    @SuppressWarnings("unused")
    private static class PluginSettingsComponent {
        private final JPanel mainPanel;
        private final JBCheckBox copyJson = new JBCheckBox(BUNDLE.getString("plugin.setting.copy.json"));
        private final JBCheckBox jsonHelper = new JBCheckBox(BUNDLE.getString("plugin.setting.json.helper"));
        private final JBCheckBox projectSearch = new JBCheckBox(BUNDLE.getString("project.search.group.name"));
        private final JBCheckBox httpSearch = new JBCheckBox(BUNDLE.getString("http.search.group.name"));
        private final JBCheckBox portSearch = new JBCheckBox(BUNDLE.getString("port.search.group.name"));
        private final JBCheckBox archiveNode = new JBCheckBox(BUNDLE.getString("plugin.setting.archive.node"));
        private final JBCheckBox rainbowBracketPair = new JBCheckBox(BUNDLE.getString("plugin.setting.rainbow.bracket.pair"));

        public PluginSettingsComponent() {
            mainPanel = FormBuilder.createFormBuilder()
                    .addComponent(of(BUNDLE.getString("plugin.setting.title1"), copyJson, jsonHelper), 1)
                    .addComponent(of(BUNDLE.getString("plugin.setting.title2"), projectSearch, httpSearch, portSearch), 1)
                    .addComponent(of(BUNDLE.getString("plugin.setting.title3"), archiveNode), 1)
                    .addComponent(of(BUNDLE.getString("plugin.setting.title4"), rainbowBracketPair), 1)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();
        }

        /**
         * 组合设置
         * @param title      标题
         * @param components 组件
         * @return {@link JPanel }
         */
        public static JPanel of(final String title, final Component... components) {
            final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            Arrays.stream(components).forEach(panel::add);
            panel.setBorder(IdeBorderFactory.createTitledBorder(title));
            return panel;
        }

        /**
         * 获取面板
         * @param callback 返回组件本体
         * @return {@link JPanel }
         */
        public JPanel getPanel(final Consumer<PluginSettingsComponent> callback) {
            if (Objects.nonNull(callback)) {
                callback.accept(this);
            }
            return mainPanel;
        }

        @NotNull
        public JComponent getPreferredFocusedComponent() {
            return jsonHelper;
        }

        public boolean getCopyJson() {
            return copyJson.isSelected();
        }

        public void setCopyJson(final boolean status) {
            copyJson.setSelected(status);
        }

        public boolean getJsonHelper() {
            return jsonHelper.isSelected();
        }

        public void setJsonHelper(final boolean status) {
            jsonHelper.setSelected(status);
        }

        public boolean getPortSearch() {
            return portSearch.isSelected();
        }

        public void setPortSearch(final boolean status) {
            portSearch.setSelected(status);
        }

        public boolean getProjectSearch() {
            return projectSearch.isSelected();
        }

        public void setProjectSearch(final boolean status) {
            projectSearch.setSelected(status);
        }

        public boolean getHttpSearch() {
            return httpSearch.isSelected();
        }

        public void setHttpSearch(final boolean status) {
            httpSearch.setSelected(status);
        }

        public boolean getArchiveNode() {
            return archiveNode.isSelected();
        }

        public void setArchiveNode(final boolean status) {
            archiveNode.setSelected(status);
        }

        public boolean getRainbowBracketPair() {
            return rainbowBracketPair.isSelected();
        }

        public void setRainbowBracketPair(final boolean status) {
            rainbowBracketPair.setSelected(status);
        }
    }
}