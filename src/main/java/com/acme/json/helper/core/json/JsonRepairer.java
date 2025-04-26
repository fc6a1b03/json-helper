package com.acme.json.helper.core.json;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import io.github.haibiiin.json.repair.JSONRepair;
import io.github.haibiiin.json.repair.JSONRepairConfig;

/**
 * JSON修复器
 *
 * @author xuhaifeng
 * @date 2025-04-26
 */
public final class JsonRepairer implements JsonOperation {
    /**
     * 维修
     *
     * @return {@link JSONRepair }
     */
    private static JSONRepair repair() {
        return new JSONRepair();
    }

    /**
     * 提取维修
     *
     * @return {@link JSONRepair }
     */
    private static JSONRepair extractRepair() {
        final JSONRepairConfig config = new JSONRepairConfig();
        config.enableExtractJSON();
        return new JSONRepair(config);
    }

    @Override
    public String process(final Object input) {
        return process(Convert.toStr(input));
    }

    @Override
    public String process(final String json) {
        if (StrUtil.isEmpty(json)) {
            return "";
        }
        try {
            try {
                return repair().handle(json);
            } catch (Exception ignored) {
                return extractRepair().handle(json);
            }
        } catch (Exception ignored) {
            return json;
        }
    }

    @Override
    public boolean isValid(final String input) {
        return Boolean.TRUE;
    }
}