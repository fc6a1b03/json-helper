package com.acme.json.helper.core.search.item;

import javax.swing.*;

/**
 * 端口搜索项<br/>
 * 表示一个本地运行的端口进程
 *
 * @param port    端口号
 * @param pid     进程ID
 * @param appName 应用名称
 * @param appPath 应用启动路径
 * @param icon    应用图标
 * @author 拒绝者
 * @date 2026-04-01
 */
public record PortSearchItem(int port, long pid, String appName, String appPath, Icon icon) {
}
