package com.acme.json.helper.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;

/**
 * 插件面板
 * @author 拒绝者
 * @date 2025-01-19
 */
public class PluginPanel extends JPanel implements Disposable {
    public PluginPanel(final Disposable parentDisposable) {
        Disposer.register(parentDisposable, this);
    }

    @Override
    public void dispose() {
        // 释放资源
    }
}