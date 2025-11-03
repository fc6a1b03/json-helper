package com.acme.json.helper.core.parser.converter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JAVA结构
 * @author 拒绝者
 * @date 2025-04-21
 */
public abstract class JavaStructure implements DataFormatConverter {
    /**
     * 默认类名
     */
    protected final static String DEFAULT_CLASS_NAME = "Dummy";

    /**
     * 处理对象
     * @param json      json串
     * @param className 类名
     * @param isRecord  是否记录
     * @return {@link ClassStructure }
     */
    protected static ClassStructure processObject(final String json, final String className, final boolean isRecord) {
        return processObject(DataFormatConverter.jsonToObject(json), className, isRecord);
    }

    /**
     * 处理对象
     * @param object    json对象
     * @param className 类名类
     * @param isRecord  是否记录
     * @return {@link ClassStructure }
     */
    protected static ClassStructure processObject(final JSONObject object, final String className, final boolean isRecord) {
        // 创建当前层级的类定义容器
        final ClassStructure currentClass = new ClassStructure(className);
        // 遍历处理每个 JSON 字段（含递归处理嵌套结构）
        object.forEach((fieldName, value) -> currentClass.fields.add(determineField(fieldName, value, currentClass, isRecord)));
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
     * @param fieldName   原始字段名称（JSON 字段名）
     * @param value       字段值（支持 JSON 对象/数组/基本类型）
     * @param parentClass 父类结构容器（用于收集嵌套类定义）
     * @param isRecord    标识父类是否为 Record 类型（影响嵌套类生成策略）
     * @return 字段定义对象，包含字段名和解析后的 Java 类型
     * @implNote 处理规则详解：
     *         <ol>
     *           <li>
     *             <b>对象类型处理</b>：
     *             <ul>
     *               <li>将 JSON 对象转换为嵌套类，类名通过 {@link #toUpperCamelCase} 转换字段名得到</li>
     *               <li>调用 {@link #processObject} 递归处理嵌套对象</li>
     *               <li>字段类型直接使用生成的嵌套类名（如：{@code address} -> {@code Address}）</li>
     *             </ul>
     *           </li>
     *           <li>
     *             <b>数组类型处理</b>：
     *             <ul>
     *               <li>空数组：默认映射为 {@code List<Object>}</li>
     *               <li>非空数组：取第一个元素判断类型
     *                 <ul>
     *                   <li>元素为对象：生成嵌套类并映射为 {@code List<ClassName>}</li>
     *                   <li>元素为基础类型：映射为 {@code List<JavaType>}</li>
     *                 </ul>
     *               </li>
     *               <li>注意：若数组中元素类型不一致，此逻辑可能不准确</li>
     *             </ul>
     *           </li>
     *           <li>
     *             <b>基础类型处理</b>：通过 {@link #getJavaType} 映射类型（如：字符串 -> {@code String}）
     *           </li>
     *         </ol>
     *         <p>
     *         &#064;example  示例：
     *         <pre>{@code
     *         // JSON 输入
     *         {
     *           "contact": { ... },          // => 生成 Contact 嵌套类，字段类型为 Contact
     *           "tags": ["A", "B", "C"],     // => 字段类型为 List<String>
     *           "history": [ { ... }, ... ]  // => 生成 History 嵌套类，字段类型为 List<History>
     *         }
     *         }</pre>
     */
    private static Field determineField(
            final String fieldName,
            final Object value,
            final ClassStructure parentClass,
            final boolean isRecord
    ) {
        // 处理 JSON 对象类型（生成嵌套类）
        if (value instanceof final JSONObject object) {
            final String nestedClassName = toUpperCamelCase(fieldName);
            parentClass.nestedClasses.add(processObject(object, nestedClassName, isRecord));
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
     * @param name 原始输入字符串（允许包含符号、小写/大写字母）
     * @return 转换后的大驼峰格式字符串。若输入为空，则原样返回。
     * @implNote 转换规则详解：
     *         <ol>
     *           <li>
     *             <b>单词分割策略</b>：
     *             使用正则表达式 {@code (?<=\\W)|(?=\\p{Lu})} 进行分割，其中：
     *             <ul>
     *               <li>{@code (?<=\\W)}：在非单词字符（即符号）的后面分割</li>
     *               <li>{@code (?=\\p{Lu})}：在大写字母的前面分割（处理驼峰边界）</li>
     *             </ul>
     *             例如：
     *             <ul>
     *               <li>"user_name" 分割为 ["user", "name"]</li>
     *               <li>"userName" 分割为 ["user", "Name"]</li>
     *               <li>"UserHTTPRequest" 分割为 ["User", "HTTP", "Request"]</li>
     *             </ul>
     *           </li>
     *           <li>
     *             <b>符号处理</b>：若单词完全由符号组成（如 "---"），则被过滤掉
     *           </li>
     *           <li>
     *             <b>首字母大写</b>：每个有效单词的首字母转为大写，其余字母保持原样
     *           </li>
     *         </ol>
     *         <p>
     *         &#064;example  示例：
     *         <pre>{@code
     *         toUpperCamelCase("user_name")     // => "UserName"
     *         toUpperCamelCase("user-firstName")// => "UserFirstName"
     *         toUpperCamelCase("UserHTTP")      // => "UserHTTP"
     *         toUpperCamelCase("hello-world!")  // => "HelloWorld"（忽略感叹号）
     *         }</pre>
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
     * @param value 价值
     * @return {@link String }
     */
    private static String getJavaType(final Object value) {
        return switch (value) {
            case final String ignored -> "String";
            case final Integer ignored -> "Integer";
            case final Long ignored -> "Long";
            case final Double ignored -> "Double";
            case final Float ignored -> "Float";
            case final Boolean ignored -> "Boolean";
            case final Byte ignored -> "Byte";
            case final Short ignored -> "Short";
            case final Character ignored -> "Character";
            case null, default -> "Object";
        };
    }

    /**
     * 收集字段类型中需要导入的包
     * <br/>
     * 该方法解析字段类型的泛型签名，提取原始类型（如将 {@code List<String>} 解析为 {@code List}），
     * 并根据原始类型判断是否需要添加对应的集合类导入语句（如 {@code java.util.List}）
     * @param type    字段类型，可能包含泛型（如 {@code Map<String, Object>}）
     * @param imports 用于收集需要导入的包名的集合（自动去重）
     * @implNote 当前支持的原始类型及对应导入包：
     *         <ul>
     *           <li>{@code List} -> {@code java.util.List}</li>
     *           <li>{@code Map}  -> {@code java.util.Map}</li>
     *           <li>{@code Set}  -> {@code java.util.Set}</li>
     *         </ul>
     *         如需支持更多类型，可在 {@code switch} 语句中扩展
     */
    protected static void collectImports(final String type, final Set<String> imports) {
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
    protected static class ClassStructure {
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
         * @param className 类名
         */
        public ClassStructure(final String className) {
            this.className = className;
        }

        public String getClassName() {
            return this.className;
        }

        public List<Field> getFields() {
            return this.fields;
        }

        public List<ClassStructure> getNestedClasses() {
            return this.nestedClasses;
        }
    }

    /**
     * 字段信息
     * @author 拒绝者
     * @date 2025-01-26
     */
    protected static class Field {
        /**
         * 字段名称
         */
        private String name;
        /**
         * 字段类型
         */
        private String type;

        public Field(final String name, final String type) {
            this.type = type;
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public Field setName(final String name) {
            this.name = name;
            return this;
        }

        public String getType() {
            return this.type;
        }

        public Field setType(final String type) {
            this.type = type;
            return this;
        }
    }
}