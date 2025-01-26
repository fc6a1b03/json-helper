package com.acme.json.helper.parser;

import cn.hutool.core.lang.Opt;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresReadLock;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 类解析器（支持嵌套结构和泛型类型）
 * @author 拒绝者
 * @date 2025-01-25
 */
public class ClassParser {
    /**
     * 将 PSI 类转换为 Map 结构
     * @param psiClass 需要解析的 PSI 类
     * @return 包含字段结构的 Map，忽略被 @JsonIgnoreType 标注的类
     */
    public static Map<String, Object> classToMap(final PsiClass psiClass) {
        return ReadAction.compute(() -> parse(psiClass));
    }

    /**
     * 解析
     * @param psiClass 需要解析的 PSI 类
     * @return {@link Map }<{@link String }, {@link Object }>
     */
    protected static Map<String, Object> parse(final PsiClass psiClass) {
        return parseInternal(psiClass, new HashSet<>());
    }

    /**
     * 解析内部
     * @param psiClass  需要解析的 PSI 类
     * @param processed 处理收集器
     * @return {@link Map }<{@link String }, {@link Object }>
     */
    @RequiresReadLock
    public static Map<String, Object> parseInternal(final PsiClass psiClass, final Set<PsiClass> processed) {
        ThreadingAssertions.assertReadAccess();
        if (processed.contains(psiClass) || Objects.nonNull(psiClass.getAnnotation(JsonIgnoreType.class.getName()))) {
            return Map.of();
        }
        processed.add(psiClass);
        try {
            return Arrays.stream(psiClass.getAllFields())
                    .sequential()
                    .filter(Objects::nonNull)
                    .filter(f -> Boolean.FALSE.equals(f.hasModifierProperty(PsiModifier.STATIC)))
                    .filter(f -> Objects.isNull(f.getAnnotation(JsonIgnore.class.getName())))
                    .map(f -> processField(f, new HashSet<>(processed)))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
        } finally {
            processed.remove(psiClass);
        }
    }

    /**
     * 处理字段
     * @param field     领域
     * @param processed 处理
     * @return {@link Map.Entry }<{@link String }, {@link Object }>
     */
    private static Map.Entry<String, Object> processField(final PsiField field, final Set<PsiClass> processed) {
        return Opt.ofNullable(TypeResolver.resolve(field.getType(), processed))
                .map(v -> Map.entry(field.getName(), v))
                .orElse(null);
    }
}