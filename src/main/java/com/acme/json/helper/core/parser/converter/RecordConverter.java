package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.convert.ConvertException;
import com.acme.json.helper.common.enums.AnyFile;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 记录转换器
 * @author 拒绝者
 * @date 2025-04-21
 */
public class RecordConverter extends JavaStructure {
    /**
     * 生成Record类的完整代码
     * <br/>
     * 该方法负责生成符合 Java Record 语法的不可变数据类代码，包含以下功能：
     * <ul>
     *   <li>初始化导包集合（自动收集字段类型依赖的标准库包）</li>
     *   <li>递归构建 Record 类及嵌套类代码</li>
     *   <li>自动合并导入语句到文件顶部</li>
     * </ul>
     * @param root 根 Record 类结构定义（包含类名、字段列表和嵌套类）
     * @return 格式化后的完整 Record 代码（含包导入和类体）
     * @implNote Record 类特性：
     *         <ul>
     *           <li>自动生成 final 修饰的不可变字段</li>
     *           <li>自动生成全字段构造方法</li>
     *           <li>字段列表以紧凑语法声明在类名后的括号中</li>
     *         </ul>
     */
    private static String generateRecordCode(final ClassStructure root) {
        // 导包集合
        final Set<String> imports = new LinkedHashSet<>();
        // 代码字符串
        final StringBuilder code = new StringBuilder(4096);
        // 构建记录类代码
        buildRecordCode(root, code, imports, 0);
        // 处理导包
        if (CollUtil.isNotEmpty(imports)) {
            final StringBuilder importBuilder = new StringBuilder();
            imports.forEach(imp -> importBuilder.append("import ").append(imp).append(";"));
            code.insert(0, importBuilder);
        }
        return Convert.toStr(code).trim();
    }

    /**
     * 递归构建 Record 类代码（含嵌套类）
     * <br/>
     * 该方法生成以下内容：
     * <ol>
     *   <li>Record 类声明（紧凑字段列表语法）</li>
     *   <li>自动生成的规范结构（不可变字段、隐式构造方法）</li>
     *   <li>嵌套 Record 或普通类代码（递归处理）</li>
     * </ol>
     * @param clazz   当前层级的类结构定义（可能为 Record 或普通类）
     * @param code    代码构建器（直接操作此对象以拼接代码）
     * @param imports 导包集合（用于收集字段类型的依赖包）
     * @param depth   递归深度（控制缩进层级：0=根类，1=一级嵌套类，依此类推）
     * @implNote Record 类语法规则：
     *         <ul>
     *           <li>类声明格式：{@code public record ClassName(Type1 field1, Type2 field2) { }}</li>
     *           <li>不支持显式定义字段（字段列表必须在类声明中直接定义）</li>
     *           <li>嵌套类需手动处理 static 修饰符（与普通类不同）</li>
     *         </ul>
     */
    private static void buildRecordCode(
            final ClassStructure clazz,
            final StringBuilder code,
            final Set<String> imports,
            final int depth
    ) {
        // 根据深度生成缩进（每层4个空格）
        final String indent = "    ".repeat(depth);
        // 生成类头
        code.append(indent)
                .append("public record ")
                .append(clazz.getClassName())
                .append("(");
        // 字段串
        final StringJoiner fieldJoiner = new StringJoiner(", ");
        // 处理字段
        clazz.getFields().forEach(f -> {
            fieldJoiner.add("%s %s".formatted(f.getType(), f.getName()));
            // 收集导包
            collectImports(f.getType(), imports);
        });
        // 处理类身体
        code.append(fieldJoiner).append(") {");
        // 处理嵌套类（递归）
        if (CollUtil.isNotEmpty(clazz.getNestedClasses())) {
            clazz.getNestedClasses()
                    .forEach(nested -> buildRecordCode(nested, code, imports, depth + 1));
        }
        // 处理闭合
        code.append(indent).append("}");
    }

    @Override
    public String convert(final String json) throws ConvertException {
        return generateRecordCode(processObject(json, DEFAULT_CLASS_NAME, Boolean.TRUE));
    }

    @Override
    public boolean support(final AnyFile any) {
        return AnyFile.RECORD.equals(any);
    }
}