package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;
import com.alibaba.fastjson2.JSON;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 类转换器
 * @author xuhaifeng
 * @date 2025-04-21
 */
public class ClassConverter extends JavaStructure {
    /**
     * 生成普通类（非 Record）的完整代码
     * <br/>
     * 该方法负责生成标准的 Java 类代码，包含 Lombok 的 {@code @Data} 注解、字段定义和嵌套类。
     * 自动处理以下逻辑：
     * <ul>
     *   <li>初始化导包集合，强制添加 {@code lombok.Data} 导入</li>
     *   <li>递归构建类代码（含嵌套类）</li>
     *   <li>根据字段类型收集需要导入的包（如 {@code java.util.List}）</li>
     *   <li>生成最终代码时自动合并导入语句到文件顶部</li>
     * </ul>
     *
     * @param root 根类结构定义（包含类名、字段列表和嵌套类）
     * @return 格式化后的完整类代码（含包导入和类体）
     */
    private static String generateClassCode(final ClassStructure root) {
        // 导包集合
        final Set<String> imports = new LinkedHashSet<>();
        // 默认带有`lombok`
        imports.add("lombok.Data");
        // 代码字符串
        final StringBuilder code = new StringBuilder(4096);
        // 构建类代码
        buildClassCode(root, code, imports, 0);
        // 处理导包
        if (CollUtil.isNotEmpty(imports)) {
            final StringBuilder importBuilder = new StringBuilder();
            imports.forEach(imp -> importBuilder.append("import ").append(imp).append(";"));
            code.insert(0, importBuilder);
        }
        return code.toString().trim();
    }

    /**
     * 递归构建类代码（含嵌套类）
     * <br/>
     * 该方法生成以下内容：
     * <ol>
     *   <li>类声明（含 {@code @Data} 注解和 {@code static} 修饰符处理）</li>
     *   <li>私有字段定义（根据 {@code ClassStructure.fields} 生成）</li>
     *   <li>嵌套类代码（递归调用自身处理）</li>
     * </ol>
     *
     * @param clazz   当前层级的类结构定义
     * @param code    代码构建器（直接操作此对象以拼接代码）
     * @param imports 导包集合（用于收集字段类型的依赖包）
     * @param depth   递归深度（控制缩进层级：0=根类，1=一级嵌套类，依此类推）
     *
     * @implNote 缩进规则：
     * <ul>
     *   <li>根类无额外缩进（例如：{@code public class Outer { ... }}）</li>
     *   <li>嵌套类每层增加 4 空格缩进（例如：{@code public static class Inner { ... }}）</li>
     * </ul>
     */
    private static void buildClassCode(
            final ClassStructure clazz,
            final StringBuilder code,
            final Set<String> imports,
            final int depth
    ) {
        // 根据深度生成缩进（每层4个空格）
        final String indent = "    ".repeat(depth);
        // 生成类头
        code.append(indent)
                .append("@Data public ")
                .append(depth > 0 ? "static " : "")
                .append("class ")
                .append(clazz.getClassName())
                .append(" {");
        // 生成字段
        clazz.getFields().forEach(f -> {
            code.append(indent)
                    .append("    private ")
                    .append(f.getType())
                    .append(" ")
                    .append(f.getName())
                    .append(";");
            collectImports(f.getType(), imports);
        });
        // 处理嵌套类（递归）
        if (CollUtil.isNotEmpty(clazz.getNestedClasses())) {
            clazz.getNestedClasses()
                    .forEach(nested -> buildClassCode(nested, code, imports, depth + 1));
        }
        code.append(indent).append("}");
    }

    @Override
    public String convert(final String json) throws ConvertException {
        return generateClassCode(processObject(JSON.parseObject(json), DEFAULT_CLASS_NAME, Boolean.FALSE));
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.CLASS.equals(any);
    }
}