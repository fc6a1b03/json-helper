package com.acme.prism.core.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 项目级释放服务
 *
 * @author 拒绝者
 * @date 2026-03-28
 */
@Service(Service.Level.PROJECT)
public final class ProjectDisposableService implements Disposable {
    /**
     * 获取项目销毁服务的实例
     * <p> 通过项目对象获取对应的项目销毁服务实例
     *
     * @param project 项目对象, 不能为 null
     * @return 项目销毁服务实例
     */
    public static ProjectDisposableService getInstance(@NotNull final Project project) {
        return project.getService(ProjectDisposableService.class);
    }

    /**
     * 释放资源
     * <p> 由 IntelliJ 平台托管生命周期, 此方法用于执行清理操作
     */
    @Override
    public void dispose() {
        // 由 IntelliJ 平台托管生命周期
    }
}
