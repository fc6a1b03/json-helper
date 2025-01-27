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
     * 基本类型及常用包装类型的默认值映射表
     *
     * <p>用于为不同类型生成随机但符合类型特征的默认值，主要场景包括：
     * <ul>
     *   <li>自动生成测试数据</li>
     *   <li>对象反序列化时的默认值填充</li>
     *   <li>动态创建示例对象时的字段初始化</li>
     * </ul>
     *
     * <p>映射规则说明：
     * <ul>
     *   <li>数值类型(int/long等) - 生成正随机数，避免负数可能引发的边界问题</li>
     *   <li>short类型 - 限制在Short.MAX_VALUE范围内</li>
     *   <li>char/byte - 使用随机字符值</li>
     *   <li>boolean - 随机真假值</li>
     *   <li>String - 10位随机字符串</li>
     * </ul>
     *
     * @implNote 使用不可变Map保证线程安全，Map.ofEntries创建的映射表具有如下特性：
     * <li>键集合包含基本类型名称和java.lang.String全限定名</li>
     * <li>所有数值类型使用绝对值保证非负</li>
     * <li>short类型通过Convert.toShort进行范围适配</li>
     * <li>虽然String不是基本类型，但因其高频使用特性特别包含在此映射表中</li>
     */
    private static final Map<String, Object> DEFAULTS = Map.ofEntries(
            entry("int", Math.abs(RandomUtil.randomInt())),
            entry("long", Math.abs(RandomUtil.randomInt())),
            entry("float", Math.abs(RandomUtil.randomFloat())),
            entry("double", Math.abs(RandomUtil.randomFloat())),
            entry("short", Math.abs(Convert.toShort(RandomUtil.randomInt(Short.MAX_VALUE)))),
            entry("char", RandomUtil.randomChar()),
            entry("byte", RandomUtil.randomChar()),
            entry("boolean", RandomUtil.randomBoolean()),
            entry("java.lang.String", RandomUtil.randomString(10))
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
        return DEFAULTS.get(type.getCanonicalText());
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
     * 解析类型并生成对应的默认值或数据结构
     *
     * @param type      需要解析的PSI类型对象（如基本类型/集合/自定义类等）
     * @param processed 已处理类型的集合，用于防止循环解析和重复处理
     * @return {@link Object}
     *         返回与类型对应的默认值或数据结构，可能的返回值包括：
     *         - 基本类型默认值（如int返回0）
     *         - 数组/集合的示例数据结构
     *         - 时间类型的格式化字符串
     *         - 随机生成的字符串
     *         - 自定义类型的递归解析结果
     *         当遇到无法解析的类型时返回null
     *
     * @implNote 方法通过ReadAction保证线程安全，适用于IntelliJ PSI模型访问。
     *           使用switch表达式处理不同类型的分支逻辑：
     *           1. 基本类型: 调用getPrimitiveDefault返回默认值
     *           2. 数组类型: 委托CollectionTypeHandler构建示例数组
     *           3. 枚举类型: 获取枚举第一个值作为示例
     *           4. 时间类型: 使用TemporalTypeHandler生成格式化时间字符串
     *           5. 集合类型: 创建包含示例元素的集合
     *           6. Map类型: 创建包含示例键值对的Map
     *           7. String类型: 生成10位随机字符串
     *           8. 自定义类型: 递归调用ClassParser进行类结构解析
     *           通过processed集合跟踪已解析类型，防止循环依赖导致的无限递归
     */
    public static Object resolve(final PsiType type, final Set<PsiClass> processed) {
        return ReadAction.compute(() -> switch (type) {
            // 基本类型（int/boolean等），返回类型默认值
            case PsiPrimitiveType pt -> getPrimitiveDefault(pt);
            // 数组类型，处理为包含示例元素的数组
            case PsiArrayType at -> CollectionTypeHandler.handleArray(at, processed);
            // 枚举类型，返回枚举的第一个常量值
            case PsiClassType ct when Opt.ofNullable(ct.resolve()).map(PsiClass::isEnum).orElse(Boolean.FALSE) ->
                    getEnumValue(ct);
            // 时间类型（如LocalDateTime），返回格式化字符串（如"2023-01-01"）
            case PsiClassType ct when TemporalTypeHandler.isTemporal(ct.resolve()) ->
                    TemporalTypeHandler.format(ct.resolve());
            // 集合类型（List/Set），创建包含示例元素的集合
            case PsiClassType ct when CollectionTypeHandler.isCollection(ct) ->
                    CollectionTypeHandler.handleCollection(ct, processed);
            // Map类型，创建包含示例键值对的Map
            case PsiClassType ct when CollectionTypeHandler.isMap(ct) -> CollectionTypeHandler.handleMap(ct, processed);
            // String类型，生成随机字符串
            case PsiClassType ct when "java.lang.String".equals(ct.getCanonicalText()) ->
                    DEFAULTS.get(ct.getCanonicalText());
            // 自定义类型，递归解析类结构
            case PsiClassType ct -> Opt.ofNullable(ct.resolve())
                    // 递归调用类解析器
                    .map(c -> ClassParser.parseInternal(c, processed))
                    // 当类无法解析时返回null
                    .orElse(null);
            // 未知类型返回null
            default -> null;
        });
    }
}