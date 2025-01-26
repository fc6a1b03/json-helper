package com.acme.json.helper.util;

import cn.hutool.core.lang.Opt;
import com.acme.json.helper.parser.TypeResolver;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 集合类型处理程序
 * @author 拒绝者
 * @date 2025-01-25
 */
public class CollectionTypeHandler {
    /**
     * 是集合
     * @param type 类型
     * @return boolean
     */
    public static boolean isCollection(final PsiClassType type) {
        return isSubType(type, CommonClassNames.JAVA_UTIL_COLLECTION) ||
                isSubType(type, CommonClassNames.JAVA_UTIL_LIST) ||
                isSubType(type, CommonClassNames.JAVA_UTIL_SET);
    }

    /**
     * 是`Map`
     * @param type 类型
     * @return boolean
     */
    public static boolean isMap(final PsiClassType type) {
        return isSubType(type, CommonClassNames.JAVA_UTIL_MAP);
    }

    /**
     * 处理数组
     * @param arrayType 数组类型
     * @param processed 处理
     * @return {@link Object }
     */
    public static Object handleArray(final PsiArrayType arrayType, final Set<PsiClass> processed) {
        if (Objects.isNull(arrayType)) {
            return null;
        }
        final PsiType componentType = arrayType.getComponentType();
        final Object element = TypeResolver.resolve(componentType, processed);
        return componentType instanceof PsiPrimitiveType ?
                createPrimitiveArray((PsiPrimitiveType) componentType, element) :
                new Object[]{element};
    }

    /**
     * 处理集合
     * @param type      类型
     * @param processed 处理
     * @return {@link Object }
     */
    public static Object handleCollection(final PsiClassType type, final Set<PsiClass> processed) {
        return Opt.ofNullable(TypeResolver.resolve(GenericInfo.fromCollection(type).elementType(), processed))
                .map(List::of)
                .orElseGet(List::of);
    }

    /**
     * 处理`Map`
     * @param type      类型
     * @param processed 处理
     * @return {@link Object }
     */
    public static Object handleMap(final PsiClassType type, final Set<PsiClass> processed) {
        final GenericInfo genericInfo = GenericInfo.fromMap(type);
        final Object key = TypeResolver.resolve(genericInfo.keyType(), processed);
        final Object value = TypeResolver.resolve(genericInfo.valueType(), processed);
        return Map.ofEntries(Map.entry(
                Opt.ofNullable(key).orElse(""), Opt.ofNullable(value).orElse("")
        ));
    }

    /**
     * 创建基本数组
     * @param type  类型
     * @param value 价值
     * @return {@link Object }
     */
    private static Object createPrimitiveArray(final PsiPrimitiveType type, final Object value) {
        return switch (type.getCanonicalText()) {
            case "int" -> new int[]{(int) value};
            case "long" -> new long[]{(long) value};
            case "float" -> new float[]{(float) value};
            case "double" -> new double[]{(double) value};
            case "boolean" -> new boolean[]{(boolean) value};
            case "char" -> new char[]{(char) value};
            case "byte" -> new byte[]{(byte) value};
            case "short" -> new short[]{(short) value};
            default -> new Object[]{value};
        };
    }

    /**
     * 是子类型
     * @param type      类型
     * @param superType 超级类型
     * @return boolean
     */
    private static boolean isSubType(final PsiClassType type, final String superType) {
        if (Objects.isNull(type)) {
            return Boolean.FALSE;
        }
        // 获取PSI类
        final PsiClass psiClass = type.resolve();
        if (Objects.isNull(psiClass)) return Boolean.FALSE;
        // 获取泛型擦除后的原始类型
        final PsiClassType rawType = getRawType(type);
        if (Objects.isNull(rawType)) return Boolean.FALSE;
        return InheritanceUtil.isInheritor(psiClass, superType);
    }

    /**
     * 获取泛型擦除后的原始类型
     * @param type 类型
     * @return {@link PsiClassType }
     */
    private static PsiClassType getRawType(final PsiClassType type) {
        final PsiType[] parameters = type.getParameters();
        if (ArrayUtil.isEmpty(parameters)) return type;
        final PsiClass psiClass = type.resolve();
        if (Objects.isNull(psiClass)) return null;
        return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
    }

    /**
     * 通用信息
     * @author 拒绝者
     * @date 2025-01-25
     */
    private record GenericInfo(PsiType keyType, PsiType valueType, PsiType elementType) {
        public static GenericInfo fromCollection(PsiClassType type) {
            final var types = type.getParameters();
            return new GenericInfo(null, null, types.length > 0 ? types[0] : null);
        }

        /**
         * 将对象转为`Map`
         * @param type 类型
         * @return {@link GenericInfo }
         */
        public static GenericInfo fromMap(final PsiClassType type) {
            final var types = type.getParameters();
            return new GenericInfo(
                    types.length > 0 ? types[0] : null,
                    types.length > 1 ? types[1] : null,
                    null
            );
        }
    }
}