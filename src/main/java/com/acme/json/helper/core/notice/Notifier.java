package com.acme.json.helper.core.notice;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * 智能线程安全通知器<br/>
 * 自动检测线程环境并选择合适的执行方式
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
        executeNotification(() -> createBasicNotification(content, NotificationType.ERROR, project, null));
    }

    /**
     * 通知警告
     * @param content 内容
     * @param project 项目
     * @param action  行动
     */
    public static void notifyWarn(final String content, final Project project, @NotNull final AnAction action) {
        executeNotification(() -> createBasicNotification(content, NotificationType.WARNING, project, action));
    }

    /**
     * 通知警告
     * @param content 内容
     * @param project 项目
     */
    public static void notifyWarn(final String content, final Project project) {
        executeNotification(() -> createBasicNotification(content, NotificationType.WARNING, project, null));
    }

    /**
     * 通知信息
     * @param content 内容
     * @param project 项目
     */
    public static void notifyInfo(final String content, final Project project) {
        executeNotification(() -> createBasicNotification(content, NotificationType.INFORMATION, project, null));
    }

    /**
     * 通知信息
     * @param content 内容
     * @param project 项目
     * @param action  行动
     */
    public static void notifyInfo(final String content, final Project project, @NotNull final AnAction action) {
        executeNotification(() -> createBasicNotification(content, NotificationType.INFORMATION, project, action));
    }

    /**
     * 通知
     * @param content 内容
     * @param project 项目
     */
    public static void notify(final String content, final NotificationType type, final Project project) {
        executeNotification(() -> createBasicNotification(content, type, project, null));
    }

    /**
     * 通知
     * @param content 内容
     * @param type    类型
     * @param project 项目
     * @param action  行动
     */
    public static void notify(@NotNull final String content, @NotNull final NotificationType type, @NotNull final Project project, @NotNull final AnAction action) {
        executeNotification(() -> createBasicNotification(content, type, project, action));
    }

    /**
     * 智能执行通知<br/>
     * 根据当前线程环境自动选择最佳执行方式
     * @param notificationTask 通知任务
     */
    private static void executeNotification(final Runnable notificationTask) {
        // 如果已经在事件分发线程，直接执行
        if (SwingUtilities.isEventDispatchThread()) {
            notificationTask.run();
        } else {
            // 在后台线程，异步切换到EDT执行
            SwingUtilities.invokeLater(notificationTask);
        }
    }

    /**
     * 创建基础通知
     * @param content 内容
     * @param type    类型
     * @param project 项目
     * @param action  行动
     */
    private static void createBasicNotification(final String content, final NotificationType type, final Project project, final AnAction action) {
        try {
            final Notification notificationBuilder = NotificationGroupManager.getInstance()
                    .getNotificationGroup("JSONGenerator.NotificationGroup")
                    .createNotification(content, type)
                    .setIcon(AllIcons.Actions.IntentionBulb);
            if (action != null) {
                notificationBuilder.addAction(action);
            }
            notificationBuilder.notify(project);
        } catch (final Exception e) {
            // 静默处理异常，避免影响主流程
            logSilently("Notification failed: %s".formatted(e.getMessage()));
        }
    }

    /**
     * 静默日志记录<br/>
     * 只在开发和调试阶段输出，避免污染生产环境日志
     * @param message 消息
     */
    private static void logSilently(final String message) {
        // 只有在开发或内部版本中才输出日志
        if (isDevelopmentEnvironment()) {
            System.err.printf("[NOTIFIER SILENT LOG] %s%n", message);
        }
    }

    /**
     * 判断是否为开发环境
     * @return boolean
     */
    private static boolean isDevelopmentEnvironment() {
        try {
            // 通过反射访问内部标志，避免直接依赖
            final Class<?> appClass = Class.forName("com.intellij.openapi.application.ApplicationManager.ApplicationManager");
            final var application = appClass.getMethod("getApplication").invoke(null);
            return (Boolean) application.getClass().getMethod("isInternal").invoke(application) ||
                    (Boolean) application.getClass().getMethod("isUnitTestMode").invoke(application);
        } catch (final Exception e) {
            return Boolean.FALSE;
        }
    }
}