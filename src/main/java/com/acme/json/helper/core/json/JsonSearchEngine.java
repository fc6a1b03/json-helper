package com.acme.json.helper.core.json;

import cn.hutool.core.lang.Opt;
import com.alibaba.fastjson2.JSONPath;

/**
 * JSON搜索引擎
 * @author 拒绝者
 * @date 2025-01-18
 */
public final class JsonSearchEngine implements JsonOperation {
    /**
     * 使用JSONPath表达式从JSON字符串中提取数据并格式化返回
     *
     * @param json       需要处理的原始JSON字符串
     * @param expression JSONPath表达式（如"$.data.items[0].name"）
     * @return {@link String}
     *         返回处理后的格式化JSON字符串，遵循以下规则：
     *         - 当提取结果为null或"null"字符串时返回空字符串
     *         - 对非null结果进行JSON格式化并去除首尾空格
     *         - 解析异常时返回空字符串
     *
     * @implNote 方法实现细节：
     * 1. 使用JSONPath引擎执行表达式查询
     * 2. 通过JsonFormatter进行结果格式化（美化缩进等）
     * 3. 使用Opt包装器进行空安全和条件过滤：
     *    - 过滤掉JSONPath可能返回的"null"字面量
     *    - 保证返回值不为null
     * 4. 异常处理策略：
     *    - 捕获所有异常（包括JSON解析错误/表达式错误）
     *    - 异常时返回空字符串保持接口稳定性
     */
    @Override
    public String process(final String json, final String expression) {
        try {
            return Opt.ofBlankAble(new JsonFormatter().process(JSONPath.eval(json, expression).toString()))
                    // 过滤null字面量
                    .filter(item -> Boolean.FALSE.equals("null".equals(item)))
                    .orElse("").trim();
        } catch (Exception e) {
            return "";
        }
    }
}