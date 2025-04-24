package com.acme.json.helper.core.console;

import com.acme.json.helper.ui.editor.JsonEditorPushProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * JSON控制台过滤器
 *
 * @author xuhaifeng
 * @date 2025-04-23
 */
public class JsonConsoleFilter implements Filter {
    /** JSON匹配模式 */
    private static final Pattern JSON_PATTERN = Pattern.compile(
            "^\\s*\\{[\\s\\S]*?}\\s*$",
            Pattern.DOTALL | Pattern.MULTILINE
    );
    /** JSON缓冲区 */
    private final StringBuilder jsonBuffer = new StringBuilder();
    /** JSON块中 */
    private boolean inJsonBlock = Boolean.FALSE;

    /**
     * 筛选器
     *
     * @param line 线
     * @param entireLength 全长
     * @return {@link Result }
     */
    @Override
    public Result applyFilter(@NotNull final String line, final int entireLength) {
        // 完整JSON单行匹配
        if (JSON_PATTERN.matcher(line).matches()) {
            return new Result(0, line.length(), new JsonHyperlinkInfo(line));
        }
        // 多行JSON起始检测
        if (line.trim().startsWith("{") && !inJsonBlock) {
            inJsonBlock = Boolean.TRUE;
            jsonBuffer.setLength(0);
            jsonBuffer.append(line).append("\n");
        }
        // 多行JSON内容收集
        else if (inJsonBlock) {
            jsonBuffer.append(line).append("\n");
            // JSON结束检测
            if (line.trim().endsWith("}")) {
                inJsonBlock = Boolean.FALSE;
                final String fullJson = jsonBuffer.toString();
                if (JSON_PATTERN.matcher(fullJson).matches()) {
                    return new Result(
                            entireLength - fullJson.length(),
                            entireLength,
                            new JsonHyperlinkInfo(fullJson.trim())
                    );
                }
            }
        }
        return null;
    }

    /**
     * JSON超链接信息
     *
     * @author xuhaifeng
     * @date 2025-04-23
     */
    private record JsonHyperlinkInfo(String json) implements HyperlinkInfo {
        /**
         * 导航
         * @param project 项目
         */
        @Override
        public void navigate(final @NotNull Project project) {
            JsonEditorPushProvider.pushToJsonEditor(project, json);
        }
    }
}