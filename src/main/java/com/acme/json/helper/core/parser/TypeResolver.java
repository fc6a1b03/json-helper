package com.acme.json.helper.core.parser;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.RandomUtil;
import com.acme.json.helper.common.CollectionTypeHandler;
import com.acme.json.helper.common.TemporalTypeHandler;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import kotlin.jvm.functions.Function0;

import java.math.BigDecimal;
import java.math.BigInteger;
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
     * @implNote 使用不可变Map保证线程安全，Map.ofEntries创建的映射表具有如下特性：
     *         <li>键集合包含基本类型名称和String全限定名</li>
     *         <li>所有数值类型使用绝对值保证非负</li>
     *         <li>short类型通过Convert.toShort进行范围适配</li>
     *         <li>虽然String不是基本类型，但因其高频使用特性特别包含在此映射表中</li>
     */
    private static final Map<String, Function0<Object>> DEFAULTS = Map.ofEntries(
            // 原始类型
            entry("char", RandomUtil::randomChar),
            entry("boolean", RandomUtil::randomBoolean),
            entry("int", () -> Math.abs(RandomUtil.randomInt())),
            entry("long", () -> Math.abs(RandomUtil.randomLong())),
            entry("float", () -> Math.abs(RandomUtil.randomFloat())),
            entry("byte", () -> RandomUtil.randomBytes(BigDecimal.ONE.intValue())),
            entry("short", () -> Math.abs(Convert.toShort(RandomUtil.randomInt(Short.MAX_VALUE)))),
            entry("double", () -> Math.abs(RandomUtil.randomDouble(BigDecimal.ONE.intValue(), Short.MAX_VALUE))),
            // 包装类型
            entry("Character", RandomUtil::randomChar),
            entry("Boolean", RandomUtil::randomBoolean),
            entry("Long", () -> Math.abs(RandomUtil.randomLong())),
            entry("Integer", () -> Math.abs(RandomUtil.randomInt())),
            entry("Float", () -> Math.abs(RandomUtil.randomFloat())),
            entry("Byte", () -> RandomUtil.randomBytes(BigDecimal.ONE.intValue())),
            entry("Short", () -> Math.abs(Convert.toShort(RandomUtil.randomInt(Short.MAX_VALUE)))),
            entry("Double", () -> Math.abs(RandomUtil.randomDouble(BigDecimal.ONE.intValue(), Short.MAX_VALUE))),
            entry("java.lang.Character", RandomUtil::randomChar),
            entry("java.lang.Boolean", RandomUtil::randomBoolean),
            entry("java.lang.Long", () -> Math.abs(RandomUtil.randomLong())),
            entry("java.lang.Integer", () -> Math.abs(RandomUtil.randomInt())),
            entry("java.lang.Float", () -> Math.abs(RandomUtil.randomFloat())),
            entry("java.lang.Byte", () -> RandomUtil.randomBytes(BigDecimal.ONE.intValue())),
            entry("java.lang.Short", () -> Math.abs(Convert.toShort(RandomUtil.randomInt(Short.MAX_VALUE)))),
            entry("java.lang.Double", () -> Math.abs(RandomUtil.randomDouble(BigDecimal.ONE.intValue(), Short.MAX_VALUE))),
            // 字符串
            entry("String", () -> RandomUtil.randomString(10)),
            entry("java.lang.String", () -> RandomUtil.randomString(10)),
            // 数值类
            entry("BigInteger", () -> new BigInteger(64, RandomUtil.getRandom())),
            entry("BigDecimal", () -> BigDecimal.valueOf(RandomUtil.randomDouble(1, Short.MAX_VALUE))),
            entry("java.math.BigInteger", () -> new BigInteger(64, RandomUtil.getRandom())),
            entry("java.math.BigDecimal", () -> BigDecimal.valueOf(RandomUtil.randomDouble(1, Short.MAX_VALUE)))
    );

    /**
     * 合并后的获取默认值方法，支持PsiType类型
     * @param type 类型
     * @return {@link Object }
     */
    public static Object getDefault(final PsiType type) {
        return Opt.ofNullable(type)
                .map(t -> DEFAULTS.get(t.getCanonicalText()))
                .map(Function0::invoke).orElse(null);
    }

    /**
     * 获取枚举值
     * @param type 需要解析的 PSI 类
     * @return {@link String }
     */
    public static String getEnumValue(final PsiClassType type) {
        return Opt.ofNullable(type.resolve())
                .stream().filter(Objects::nonNull)
                .flatMap(c -> Arrays.stream(c.getAllFields()))
                .filter(Objects::nonNull).filter(PsiEnumConstant.class::isInstance)
                .findFirst().map(PsiField::getName).orElse("");
    }

    /**
     * 解析类型并生成对应的默认值或数据结构
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
     * @implNote 方法通过ReadAction保证线程安全，适用于IntelliJ PSI模型访问。
     */
    public static Object resolve(final PsiType type, final Set<PsiClass> processed) {
        return ReadAction.compute(() -> switch (type) {
            // 基本类型，返回类型默认值
            case final PsiPrimitiveType pt -> getDefault(pt);
            // 其他基础类型
            case final PsiClassType ct when DEFAULTS.containsKey(ct.getCanonicalText()) -> getDefault(ct);
            // 数组类型，处理为包含示例元素的数组
            case final PsiArrayType at -> CollectionTypeHandler.handleArray(at, processed);
            // 枚举类型，返回枚举的第一个常量值
            case final PsiClassType ct when ct.resolve() instanceof final PsiClass c && c.isEnum() -> getEnumValue(ct);
            // 时间类型（如LocalDate），返回格式化字符串（如"2023-01-01"）
            case final PsiClassType ct when ct.resolve() instanceof final PsiClass c && TemporalTypeHandler.isTemporal(c) ->
                    TemporalTypeHandler.format(c);
            // 集合类型（List/Set），创建包含示例元素的集合
            case final PsiClassType ct when CollectionTypeHandler.isCollection(ct) ->
                    CollectionTypeHandler.handleCollection(ct, processed);
            // Map类型，创建包含示例键值对的Map
            case final PsiClassType ct when CollectionTypeHandler.isMap(ct) ->
                    CollectionTypeHandler.handleMap(ct, processed);
            // 自定义类型，递归解析类结构
            case final PsiClassType ct -> Opt.ofNullable(ct.resolve())
                    // 递归调用类解析器
                    .map(c -> ClassParser.parseInternal(c, processed))
                    // 当类无法解析时返回null
                    .orElse(null);
            // 未知类型返回null
            default -> null;
        });
    }
}