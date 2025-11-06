package com.acme.json.helper.core.notice;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 通知器
 * @author 拒绝者
 * @date 2025-01-25
 */
@SuppressWarnings("unused")
public class Notifier {
    /**
     * 通知错误
     * @param content 内容
     * @param project 项目
     */
    public static void notifyError(final String content, final Project project) {
        notify(content, NotificationType.ERROR, project);
    }

    /**
     * 通知警告
     * @param content 内容
     * @param project 项目
     * @param action  行动
     */
    public static void notifyWarn(final String content, final Project project, @NotNull final AnAction action) {
        notify(content, NotificationType.WARNING, project, action);
    }

    /**
     * 通知警告
     * @param content 内容
     * @param project 项目
     */
    public static void notifyWarn(final String content, final Project project) {
        notify(content, NotificationType.WARNING, project);
    }

    /**
     * 通知信息
     * @param content 内容
     * @param project 项目
     */
    public static void notifyInfo(final String content, final Project project) {
        notify(content, NotificationType.INFORMATION, project);
    }

    /**
     * 通知信息
     * @param content 内容
     * @param project 项目
     * @param action  行动
     */
    public static void notifyInfo(final String content, final Project project, @NotNull final AnAction action) {
        notify(content, NotificationType.INFORMATION, project, action);
    }

    /**
     * 通知
     * @param content 内容
     * @param type    类型
     * @param project 项目
     */
    public static void notify(final String content, final NotificationType type, final Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("JSONGenerator.NotificationGroup")
                .createNotification(content, type)
                .setIcon(AllIcons.Actions.IntentionBulb)
                .notify(project);
    }

    /**
     * 通知
     * @param content 内容
     * @param type    类型
     * @param project 项目
     */
    public static void notify(@NotNull final String content, @NotNull final NotificationType type, @NotNull final Project project, @NotNull final AnAction action) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("JSONGenerator.NotificationGroup")
                .createNotification(content, type)
                .setIcon(AllIcons.Actions.IntentionBulb)
                .addAction(action).notify(project);
    }
}