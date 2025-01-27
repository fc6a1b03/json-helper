package com.acme.json.helper.core.parser;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JSON解析器（支持嵌套结构）
 * @author 拒绝者
 * @date 2025-01-26
 */
public class JsonParser {
    /**
     * 默认类名
     */
    private final static String DEFAULT_CLASS_NAME = "Dummy";

    /**
     * 转换为JSON记录
     * @param jsonText JSON文本
     * @return {@link String }
     */
    public static String convertToJsonRecord(final String jsonText) {
        return generateRecordCode(processObject(JSON.parseObject(jsonText), DEFAULT_CLASS_NAME, Boolean.TRUE));
    }

    /**
     * 转换为JSON类
     * @param jsonText JSON文本
     * @return {@link String }
     */
    public static String convertToJsonClass(final String jsonText) {
        return generateClassCode(processObject(JSON.parseObject(jsonText), DEFAULT_CLASS_NAME, Boolean.FALSE));
    }

    /**
     * 处理 JSON 对象并生成对应的类结构定义
     * <br/>
     * 该方法将 JSON 对象解析为 Java 类结构，包含以下逻辑：
     * <ul>
     *   <li>创建指定类名的类结构容器</li>
     *   <li>递归解析 JSON 字段（支持嵌套对象/数组）</li>
     *   <li>自动生成嵌套类结构（通过 {@link #determineField} 触发）</li>
     *   <li>统一处理 Record 和普通类的生成策略</li>
     * </ul>
     *
     * @param jsonObject 要处理的 JSON 对象（非空）
     * @param className  生成的类名（通常由字段名经驼峰转换得到）
     * @param isRecord   标识生成的类是否为 Record 类型
     * @return 包含所有字段和嵌套类定义的类结构
     *
     * @implNote 核心处理流程：
     * <ol>
     *   <li>创建空类结构容器：{@code new ClassStructure(className)}</li>
     *   <li>遍历 JSON 对象的每个字段：
     *     <ul>
     *       <li>对每个字段调用 {@link #determineField} 解析字段类型</li>
     *       <li>自动处理嵌套结构（如对象类型会生成新类并添加到嵌套类列表）</li>
     *     </ul>
     *   </li>
     *   <li>返回完整的类结构定义（可能包含嵌套类）</li>
     * </ol>
     *
     * &#064;example  示例：
     * <pre>{@code
     * // 输入 JSON
     * {
     *   "name": "John",
     *   "address": {
     *     "street": "Main St",
     *     "city": "NY"
     *   }
     * }
     *
     * // 生成的类结构
     * ClassStructure:
     *   className: "User"
     *   fields: [
     *     Field("name", "String"),
     *     Field("address", "Address") // 嵌套类
     *   ]
     *   nestedClasses: [
     *     ClassStructure:
     *       className: "Address"
     *       fields: [
     *         Field("street", "String"),
     *         Field("city", "String")
     *       ]
     *   ]
     * }</pre>
     */
    private static ClassStructure processObject(final JSONObject jsonObject, final String className, final boolean isRecord) {
        // 创建当前层级的类定义容器
        final ClassStructure currentClass = new ClassStructure(className);
        // 遍历处理每个 JSON 字段（含递归处理嵌套结构）
        jsonObject.forEach((fieldName, value) -> currentClass.fields.add(determineField(fieldName, value, currentClass, isRecord)));
        return currentClass;
    }

    /**
     * 根据字段值类型确定字段定义，并处理嵌套结构
     * <br/>
     * 该方法根据 JSON 字段值的实际类型生成对应的 Java 字段定义，处理以下场景：
     * <ul>
     *   <li>对象类型（{@link JSONObject}）：生成嵌套类并添加到父类结构中</li>
     *   <li>数组类型（{@link JSONArray}）：根据数组元素类型生成泛型集合类型</li>
     *   <li>基本类型：映射为对应的 Java 基础类型/包装类</li>
     * </ul>
     *
     * @param fieldName    原始字段名称（JSON 字段名）
     * @param value        字段值（支持 JSON 对象/数组/基本类型）
     * @param parentClass  父类结构容器（用于收集嵌套类定义）
     * @param isRecord     标识父类是否为 Record 类型（影响嵌套类生成策略）
     * @return 字段定义对象，包含字段名和解析后的 Java 类型
     *
     * @implNote 处理规则详解：
     * <ol>
     *   <li>
     *     <b>对象类型处理</b>：
     *     <ul>
     *       <li>将 JSON 对象转换为嵌套类，类名通过 {@link #toUpperCamelCase} 转换字段名得到</li>
     *       <li>调用 {@link #processObject} 递归处理嵌套对象</li>
     *       <li>字段类型直接使用生成的嵌套类名（如：{@code address} -> {@code Address}）</li>
     *     </ul>
     *   </li>
     *   <li>
     *     <b>数组类型处理</b>：
     *     <ul>
     *       <li>空数组：默认映射为 {@code List<Object>}</li>
     *       <li>非空数组：取第一个元素判断类型
     *         <ul>
     *           <li>元素为对象：生成嵌套类并映射为 {@code List<ClassName>}</li>
     *           <li>元素为基础类型：映射为 {@code List<JavaType>}</li>
     *         </ul>
     *       </li>
     *       <li>注意：若数组中元素类型不一致，此逻辑可能不准确</li>
     *     </ul>
     *   </li>
     *   <li>
     *     <b>基础类型处理</b>：通过 {@link #getJavaType} 映射类型（如：字符串 -> {@code String}）
     *   </li>
     * </ol>
     *
     * &#064;example  示例：
     * <pre>{@code
     * // JSON 输入
     * {
     *   "contact": { ... },          // => 生成 Contact 嵌套类，字段类型为 Contact
     *   "tags": ["A", "B", "C"],     // => 字段类型为 List<String>
     *   "history": [ { ... }, ... ]  // => 生成 History 嵌套类，字段类型为 List<History>
     * }
     * }</pre>
     */
    private static Field determineField(
            final String fieldName,
            final Object value,
            final ClassStructure parentClass,
            final boolean isRecord
    ) {
        // 处理 JSON 对象类型（生成嵌套类）
        if (value instanceof JSONObject) {
            final String nestedClassName = toUpperCamelCase(fieldName);
            parentClass.nestedClasses.add(processObject((JSONObject) value, nestedClassName, isRecord));
            return new Field(fieldName, nestedClassName);
        }
        // 处理 JSON 数组类型
        if (value instanceof final JSONArray array) {
            if (CollUtil.isEmpty(array)) {
                // 空数组默认使用 Object 类型
                return new Field(fieldName, "List<Object>");
            } else {
                // 根据第一个元素推断类型
                final Object firstElement = array.getFirst();
                if (firstElement instanceof JSONObject) {
                    // 元素为对象时生成嵌套类
                    final String elementClassName = toUpperCamelCase(fieldName);
                    parentClass.nestedClasses.add(processObject((JSONObject) firstElement, elementClassName, isRecord));
                    return new Field(fieldName, "List<%s>".formatted(elementClassName));
                } else {
                    // 基础类型直接映射
                    return new Field(fieldName, "List<%s>".formatted(getJavaType(firstElement)));
                }
            }
        }
        // 处理基础类型（String/Number/Boolean 等）
        return new Field(fieldName, getJavaType(value));
    }

    /**
     * 将字符串转换为大驼峰命名格式（Upper Camel Case）
     * <br/>
     * 该方法处理以下场景：
     * <ul>
     *   <li>分割符号或大写字母作为单词边界（如：将 "user_name" 或 "userName" 统一拆分为 ["user", "name"]）</li>
     *   <li>过滤掉纯符号部分（如：保留 "hello-world" 中的 "hello" 和 "world"，忽略中间的 "-"）</li>
     *   <li>每个单词首字母大写并拼接（如："hello_world" -> "HelloWorld"）</li>
     * </ul>
     *
     * @param name 原始输入字符串（允许包含符号、小写/大写字母）
     * @return 转换后的大驼峰格式字符串。若输入为空，则原样返回。
     *
     * @implNote 转换规则详解：
     * <ol>
     *   <li>
     *     <b>单词分割策略</b>：
     *     使用正则表达式 {@code (?<=\\W)|(?=\\p{Lu})} 进行分割，其中：
     *     <ul>
     *       <li>{@code (?<=\\W)}：在非单词字符（即符号）的后面分割</li>
     *       <li>{@code (?=\\p{Lu})}：在大写字母的前面分割（处理驼峰边界）</li>
     *     </ul>
     *     例如：
     *     <ul>
     *       <li>"user_name" 分割为 ["user", "name"]</li>
     *       <li>"userName" 分割为 ["user", "Name"]</li>
     *       <li>"UserHTTPRequest" 分割为 ["User", "HTTP", "Request"]</li>
     *     </ul>
     *   </li>
     *   <li>
     *     <b>符号处理</b>：若单词完全由符号组成（如 "---"），则被过滤掉
     *   </li>
     *   <li>
     *     <b>首字母大写</b>：每个有效单词的首字母转为大写，其余字母保持原样
     *   </li>
     * </ol>
     *
     * &#064;example  示例：
     * <pre>{@code
     * toUpperCamelCase("user_name")     // => "UserName"
     * toUpperCamelCase("user-firstName")// => "UserFirstName"
     * toUpperCamelCase("UserHTTP")      // => "UserHTTP"
     * toUpperCamelCase("hello-world!")  // => "HelloWorld"（忽略感叹号）
     * }</pre>
     */
    private static String toUpperCamelCase(final String name) {
        if (StrUtil.isEmpty(name)) {
            return name;
        }
        // 使用正则表达式分割单词边界：非字母数字符或驼峰大写位置
        return Arrays.stream(name.split("(?<=\\W)|(?=\\p{Lu})"))
                .filter(StrUtil::isNotEmpty)
                .map(word -> {
                    // 忽略纯符号（如 "--"、"$$" 等）
                    if (word.matches("\\W+")) {
                        return "";
                    }
                    // 首字母大写（保留后续字符原样）
                    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
                })
                .collect(Collectors.joining());
    }

    /**
     * 获取JAVA类型
     *
     * @param value 价值
     * @return {@link String }
     */
    private static String getJavaType(final Object value) {
        return switch (value) {
            case String ignored -> "String";
            case Integer ignored -> "int";
            case Long ignored -> "long";
            case Double ignored -> "double";
            case Boolean ignored -> "boolean";
            case null, default -> "Object";
        };
    }

    /**
     * 生成Record类的完整代码
     * <br/>
     * 该方法负责生成符合 Java Record 语法的不可变数据类代码，包含以下功能：
     * <ul>
     *   <li>初始化导包集合（自动收集字段类型依赖的标准库包）</li>
     *   <li>递归构建 Record 类及嵌套类代码</li>
     *   <li>自动合并导入语句到文件顶部</li>
     * </ul>
     *
     * @param root 根 Record 类结构定义（包含类名、字段列表和嵌套类）
     * @return 格式化后的完整 Record 代码（含包导入和类体）
     *
     * @implNote Record 类特性：
     * <ul>
     *   <li>自动生成 final 修饰的不可变字段</li>
     *   <li>自动生成全字段构造方法</li>
     *   <li>字段列表以紧凑语法声明在类名后的括号中</li>
     * </ul>
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
        return code.toString().trim();
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
     *
     * @param clazz   当前层级的类结构定义（可能为 Record 或普通类）
     * @param code    代码构建器（直接操作此对象以拼接代码）
     * @param imports 导包集合（用于收集字段类型的依赖包）
     * @param depth   递归深度（控制缩进层级：0=根类，1=一级嵌套类，依此类推）
     *
     * @implNote Record 类语法规则：
     * <ul>
     *   <li>类声明格式：{@code public record ClassName(Type1 field1, Type2 field2) { }}</li>
     *   <li>不支持显式定义字段（字段列表必须在类声明中直接定义）</li>
     *   <li>嵌套类需手动处理 static 修饰符（与普通类不同）</li>
     * </ul>
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
                .append(clazz.className)
                .append("(");
        // 字段串
        final StringJoiner fieldJoiner = new StringJoiner(", ");
        // 处理字段
        clazz.fields.forEach(f -> {
            fieldJoiner.add("%s %s".formatted(f.type, f.name));
            // 收集导包
            collectImports(f.type, imports);
        });
        // 处理类身体
        code.append(fieldJoiner).append(") {");
        // 处理嵌套类（递归）
        if (CollUtil.isNotEmpty(clazz.nestedClasses)) {
            clazz.nestedClasses
                    .forEach(nested -> buildRecordCode(nested, code, imports, depth + 1));
        }
        // 处理闭合
        code.append(indent).append("}");
    }

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
                .append(clazz.className)
                .append(" {");
        // 生成字段
        clazz.fields.forEach(f -> {
            code.append(indent)
                    .append("    private ")
                    .append(f.type)
                    .append(" ")
                    .append(f.name)
                    .append(";");
            collectImports(f.type, imports);
        });
        // 处理嵌套类（递归）
        if (CollUtil.isNotEmpty(clazz.nestedClasses)) {
            clazz.nestedClasses
                    .forEach(nested -> buildClassCode(nested, code, imports, depth + 1));
        }
        code.append(indent).append("}");
    }

    /**
     * 收集字段类型中需要导入的包
     * <br/>
     * 该方法解析字段类型的泛型签名，提取原始类型（如将 {@code List<String>} 解析为 {@code List}），
     * 并根据原始类型判断是否需要添加对应的集合类导入语句（如 {@code java.util.List}）
     *
     * @param type    字段类型，可能包含泛型（如 {@code Map<String, Object>}）
     * @param imports 用于收集需要导入的包名的集合（自动去重）
     *
     * @implNote 当前支持的原始类型及对应导入包：
     * <ul>
     *   <li>{@code List} -> {@code java.util.List}</li>
     *   <li>{@code Map}  -> {@code java.util.Map}</li>
     *   <li>{@code Set}  -> {@code java.util.Set}</li>
     * </ul>
     * 如需支持更多类型，可在 {@code switch} 语句中扩展
     */
    private static void collectImports(final String type, final Set<String> imports) {
        // 提取原始类型（处理泛型场景：例如将 "List<String>" 解析为 "List"）
        final int genericIndex = type.indexOf('<');
        // 根据原始类型添加对应导入包
        switch (
                Opt.of(genericIndex == -1)
                        .filter(i -> i)
                        // 无泛型：直接取完整类型
                        .map(item -> type.trim())
                        // 有泛型：截取泛型前的基础类型
                        .orElseGet(() -> type.substring(0, genericIndex).trim())
        ) {
            case "List" -> imports.add("java.util.List");
            case "Map" -> imports.add("java.util.Map");
            case "Set" -> imports.add("java.util.Set");
            // 可在此扩展其他需要导入的标准库集合类型
        }
    }

    /**
     * 类结构
     * @author 拒绝者
     * @date 2025-01-26
     */
    private static class ClassStructure {
        /**
         * 类名称
         */
        private final String className;
        /**
         * 字段集合
         */
        private final List<Field> fields = new ArrayList<>();
        /**
         * 嵌套类集合
         */
        private final List<ClassStructure> nestedClasses = new ArrayList<>();

        /**
         * 构建新的类结构对象
         *
         * @param className 类名
         */
        public ClassStructure(final String className) {
            this.className = className;
        }
    }

    /**
     * 字段信息
     * @author 拒绝者
     * @date 2025-01-26
     */
    private record Field(String name, String type) {
    }
}