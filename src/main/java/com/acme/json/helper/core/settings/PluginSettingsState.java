package com.acme.json.helper.core.settings;

import cn.hutool.core.lang.Opt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 设置状态
 *
 * @author 拒绝者
 * @date 2025-02-02
 */
@State(
        name = "com.acme.json.helper.core.settings.SettingsState",
        storages = @Storage("JsonHelperGlobal.xml")
)
public class PluginSettingsState implements PersistentStateComponent<PluginSettingsState> {
    /** 复制JSON默认配置 */
    public boolean copyJson = Boolean.TRUE;
    /** JSON助手默认配置 */
    public boolean jsonHelper = Boolean.TRUE;

    @NotNull
    public static PluginSettingsState getInstance() {
        return Opt.ofNullable(ApplicationManager.getApplication().getService(PluginSettingsState.class)).orElseGet(PluginSettingsState::new);
    }

    @Nullable
    @Override
    public PluginSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull PluginSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}