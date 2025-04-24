package com.acme.json.helper.core.console;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * JSON日志监听器
 *
 * @author xuhaifeng
 * @date 2025-04-23
 */
public class JsonFilterProvider implements ConsoleFilterProvider {
    /**
     * 获取默认筛选器
     *
     * @param project 项目
     * @return {@link Filter } {@link @NotNull } {@link [] }
     */
    @Override
    public Filter @NotNull [] getDefaultFilters(@NotNull final Project project) {
        return new Filter[]{new JsonConsoleFilter()};
    }
}