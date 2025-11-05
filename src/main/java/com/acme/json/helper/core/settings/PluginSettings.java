package com.acme.json.helper.core.settings;

import com.intellij.openapi.options.Configurable;
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
        boolean modified = component.getCopyJson() != settings.copyJson;
        modified |= component.getJsonHelper() != settings.jsonHelper;
        return modified;
    }

    @Override
    public void apply() {
        // 获取设置状态
        final PluginSettingsState settings = of();
        // 将设置组件状态覆盖全局状态
        settings.copyJson = component.getCopyJson();
        settings.jsonHelper = component.getJsonHelper();
    }

    @Override
    public void reset() {
        final PluginSettingsState settings = of();
        component.setCopyJson(settings.copyJson);
        component.setJsonHelper(settings.jsonHelper);
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

        public PluginSettingsComponent() {
            mainPanel = FormBuilder.createFormBuilder()
                    .addComponent(of(BUNDLE.getString("plugin.setting.title1"), copyJson, jsonHelper), 1)
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
    }
}