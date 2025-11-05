package com.acme.json.helper.ui.editor.record;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 编辑器状态
 * @author 拒绝者
 * @date 2025-11-04
 */
public record EditorState(Integer editorId, String content) {
    /**
     * ASCII Record Separator 分隔符（外）
     */
    private static final String SEP_OUTSIDE = "\u001E";
    /**
     * ASCII Record Separator 分隔符（内）
     */
    private static final String SEP_INTERNAL = "\u001F";
    /**
     * JsonHelper 状态密钥
     */
    public static final String JSON_HELPER_STATE_KEY = "json:helper:state";
    /**
     * 存储幂等
     */
    public static final Set<String> SAVED_MARK = ConcurrentHashMap.newKeySet();

    /**
     * 编码
     * @param stateList 列表
     * @return {@link String }
     */
    public static String encode(final List<EditorState> stateList) {
        return CollUtil.emptyIfNull(stateList).stream().filter(Objects::nonNull)
                .map(s -> "%d%s%s".formatted(
                        s.editorId, SEP_INTERNAL,
                        Base64.getEncoder().encodeToString(StrUtil.bytes(s.content, StandardCharsets.UTF_8))
                )).collect(Collectors.joining(SEP_OUTSIDE));
    }

    /**
     * 解码
     * @param raw 原始
     * @return {@link List }<{@link EditorState }>
     */
    public static List<EditorState> decode(final String raw) {
        return Arrays.stream(StrUtil.emptyIfNull(raw).split(SEP_OUTSIDE)).filter(StrUtil::isNotBlank)
                .map(str -> str.split(SEP_INTERNAL)).filter(ArrayUtil::isNotEmpty)
                .map(strs -> new EditorState(Convert.toInt(strs[0]), StrUtil.utf8Str(Base64.getDecoder().decode(strs[1])))).toList();
    }
}