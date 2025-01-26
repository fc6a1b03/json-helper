package com.acme.json.helper.core.parser;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.RandomUtil;
import com.acme.json.helper.common.CollectionTypeHandler;
import com.acme.json.helper.common.TemporalTypeHandler;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Map.entry;

/**
 * 类型解析器
 * @author 拒绝者
 * @date 2025-01-25
 */
public class TypeResolver {
    /**
     * 原始默认值
     */
    private static final Map<String, Object> PRIMITIVE_DEFAULTS = Map.ofEntries(
            entry("int", Math.abs(RandomUtil.randomInt())),
            entry("long", Math.abs(RandomUtil.randomInt())),
            entry("float", Math.abs(RandomUtil.randomFloat())),
            entry("double", Math.abs(RandomUtil.randomFloat())),
            entry("short", Math.abs(Convert.toShort(RandomUtil.randomInt(Short.MAX_VALUE)))),
            entry("char", RandomUtil.randomChar()),
            entry("byte", RandomUtil.randomChar()),
            entry("boolean", RandomUtil.randomBoolean())
    );

    /**
     * 获取原始默认值
     * @param type 类型
     * @return {@link Object }
     */
    public static Object getPrimitiveDefault(final PsiPrimitiveType type) {
        if (Objects.isNull(type)) {
            return null;
        }
        return PRIMITIVE_DEFAULTS.get(type.getCanonicalText());
    }

    /**
     * 获取枚举值
     * @param type 需要解析的 PSI 类
     * @return {@link String }
     */
    public static String getEnumValue(final PsiClassType type) {
        return Opt.ofNullable(type.resolve())
                .stream()
                .flatMap(c -> Arrays.stream(c.getAllFields()))
                .filter(PsiEnumConstant.class::isInstance)
                .findFirst()
                .map(PsiField::getName)
                .orElse("");
    }

    /**
     * 确认类型
     * @param type      类型
     * @param processed 处理
     * @return {@link Object }
     */
    public static Object resolve(final PsiType type, final Set<PsiClass> processed) {
        return ReadAction.compute(() -> switch (type) {
            // 基本类型
            case PsiPrimitiveType pt -> getPrimitiveDefault(pt);
            // 数组类型
            case PsiArrayType at -> CollectionTypeHandler.handleArray(at, processed);
            // 枚举类型
            case PsiClassType ct when Opt.ofNullable(ct.resolve()).map(PsiClass::isEnum).orElse(Boolean.FALSE) ->
                    getEnumValue(ct);
            // 时间类型
            case PsiClassType ct when TemporalTypeHandler.isTemporal(ct.resolve()) ->
                    TemporalTypeHandler.format(ct.resolve());
            // 集合类型
            case PsiClassType ct when CollectionTypeHandler.isCollection(ct) ->
                    CollectionTypeHandler.handleCollection(ct, processed);
            // Map类型
            case PsiClassType ct when CollectionTypeHandler.isMap(ct) -> CollectionTypeHandler.handleMap(ct, processed);
            // String类型
            case PsiClassType ct when "java.lang.String".equals(ct.getCanonicalText()) -> RandomUtil.randomString(10);
            // 其他自定义类型递归解析
            case PsiClassType ct -> Opt.ofNullable(ct.resolve())
                    .map(c -> ClassParser.parseInternal(c, processed))
                    .orElse(null);
            default -> null;
        });
    }
}