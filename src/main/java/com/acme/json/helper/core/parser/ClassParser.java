package com.acme.json.helper.core.parser;

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
 * 类解析器（支持嵌套结构和泛型类型）<br/>
 * JAVA转JSON
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
        return ReadAction.compute(() -> parseInternal(psiClass, new HashSet<>()));
    }

    /**
     * 递归解析 PSI 类结构并生成结构化数据映射
     * <br/>
     * 本方法用于深度解析类的字段结构，生成可用于 JSON 序列化/反序列化的字段映射关系，处理以下逻辑：
     * <ul>
     *   <li>防止循环引用：通过 {@code processed} 集合跟踪已处理的类</li>
     *   <li>注解过滤：自动跳过带有 {@link JsonIgnore @JsonIgnore} 和 {@link JsonIgnoreType @JsonIgnoreType} 的字段和类</li>
     *   <li>线程安全：强制要求在读锁环境下执行（通过 {@link ThreadingAssertions#assertReadAccess()} 验证）</li>
     * </ul>
     *
     * @param psiClass   要解析的 PSI 类对象（不可为空）
     * @param processed  已处理类集合（用于防止循环引用，调用方需初始化空集合）
     * @return 有序字段映射表（LinkedHashMap 保证字段顺序）：
     *         <ul>
     *           <li>Key: 字段名称</li>
     *           <li>Value: 字段类型解析结果（基础类型/嵌套对象结构）</li>
     *         </ul>
     *
     * @throws IllegalStateException 如果不在读线程上下文中调用
     *
     * @implNote 核心处理流程：
     * <ol>
     *   <li>安全性检查：
     *     <ul>
     *       <li>验证当前线程是否持有读锁（IDE 插件线程模型要求）</li>
     *       <li>跳过已处理或标记为 {@code @JsonIgnoreType} 的类</li>
     *     </ul>
     *   </li>
     *   <li>字段处理：
     *     <ul>
     *       <li>过滤静态字段（{@code PsiModifier.STATIC}）</li>
     *       <li>过滤标记为 {@code @JsonIgnore} 的字段</li>
     *       <li>递归处理嵌套类型（通过 {@link #processField} 触发）</li>
     *     </ul>
     *   </li>
     *   <li>结果构造：
     *     <ul>
     *       <li>使用 {@code LinkedHashMap} 保留字段声明顺序</li>
     *       <li>合并策略：字段重复时保留首次出现的值（{@code (a, b) -> a}）</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * &#064;example  典型场景：
     * <pre>{@code
     * // 给定类结构
     * class User {
     *     private String name;
     *     @JsonIgnore
     *     private int age;          // 被忽略
     *     private Address address; // 嵌套对象
     * }
     *
     * // 解析结果
     * Map.of(
     *     "name", String.class,
     *     "address", Map.of(        // 嵌套结构
     *         "street", String.class,
     *         "city", String.class
     *     )
     * )
     * }</pre>
     */
    @RequiresReadLock
    public static Map<String, Object> parseInternal(
            final PsiClass psiClass,
            final Set<PsiClass> processed
    ) {
        // 强制线程安全检查（IDE 插件开发要求）
        ThreadingAssertions.assertReadAccess();
        // 终止条件：已处理类或标记忽略的类型
        if (processed.contains(psiClass) ||
                Objects.nonNull(psiClass.getAnnotation(JsonIgnoreType.class.getName()))) {
            return Map.of();
        }
        // 标记为已处理
        processed.add(psiClass);
        try {
            return Arrays.stream(psiClass.getAllFields())
                    // 保持字段声明顺序
                    .sequential()
                    .filter(Objects::nonNull)
                    // 过滤静态字段
                    .filter(f -> !f.hasModifierProperty(PsiModifier.STATIC))
                    // 过滤被 @JsonIgnore 标记的字段
                    .filter(f -> Objects.isNull(f.getAnnotation(JsonIgnore.class.getName())))
                    // 递归处理字段类型（传递 processed 集合防止循环）
                    .map(f -> processField(f, new HashSet<>(processed)))
                    .filter(Objects::nonNull)
                    // 构建有序映射（保留字段声明顺序）
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            // 合并策略：重复字段保留第一个
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
        } finally {
            // 重要：完成处理后移除标记，允许其他路径再次解析
            processed.remove(psiClass);
        }
    }

    /**
     * 处理指定字段并生成字段名与解析类型信息的键值对
     *
     * @param field     需要处理的字段对象，包含字段类型和名称信息
     * @param processed 已处理类型的集合，用于防止循环解析和重复处理
     * @return {@link Map.Entry}<{@link String}, {@link Object}>
     *         键为字段名称，值为解析后的类型信息。
     *         当类型解析失败时返回null
     *
     * @implNote 该方法通过TypeResolver解析字段类型，使用Opt包装避免空指针异常。
     *           processed集合用于记录已处理的PsiClass，确保递归解析时不会进入死循环。
     *           当解析结果为null时（如遇到无法解析的泛型类型或循环引用），返回null值
     */
    private static Map.Entry<String, Object> processField(final PsiField field, final Set<PsiClass> processed) {
        return Opt.ofNullable(TypeResolver.resolve(field.getType(), processed))
                .map(v -> Map.entry(field.getName(), v))
                .orElse(null);
    }
}