package com.acme.json.helper.core.parser;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    public String convertToJsonRecord(final String jsonText) {
        return generateRecordCode(processObject(JSON.parseObject(jsonText), DEFAULT_CLASS_NAME, Boolean.TRUE));
    }

    /**
     * 转换为JSON类
     * @param jsonText JSON文本
     * @return {@link String }
     */
    public String convertToJsonClass(final String jsonText) {
        return generateClassCode(processObject(JSON.parseObject(jsonText), DEFAULT_CLASS_NAME, Boolean.FALSE));
    }

    /**
     * 处理对象
     * @param jsonObject json对象
     * @param className  类名
     * @param isRecord   是记录
     * @return {@link ClassStructure }
     */
    private ClassStructure processObject(final JSONObject jsonObject, final String className, final boolean isRecord) {
        final ClassStructure currentClass = new ClassStructure(className);
        jsonObject.forEach((fieldName, value) -> currentClass.fields.add(determineField(fieldName, value, currentClass, isRecord)));
        return currentClass;
    }

    /**
     * 确定字段
     * @param fieldName   字段名称
     * @param value       价值
     * @param parentClass 父类
     * @param isRecord    是记录
     * @return {@link Field }
     */
    private Field determineField(final String fieldName, final Object value, final ClassStructure parentClass, final boolean isRecord) {
        if (value instanceof JSONObject) {
            final String nestedClassName = generateNestedClassName(fieldName);
            parentClass.nestedClasses.add(processObject((JSONObject) value, nestedClassName, isRecord));
            return new Field(fieldName, nestedClassName);
        } else if (value instanceof final JSONArray array) {
            if (array.isEmpty()) {
                return new Field(fieldName, "List<Object>");
            } else {
                final Object firstElement = array.getFirst();
                if (firstElement instanceof JSONObject) {
                    final String elementClassName = generateNestedClassName(fieldName);
                    parentClass.nestedClasses.add(processObject((JSONObject) firstElement, elementClassName, isRecord));
                    return new Field(fieldName, "List<%s>".formatted(elementClassName));
                } else {
                    return new Field(fieldName, "List<%s>".formatted(getJavaType(firstElement)));
                }
            }
        } else {
            return new Field(fieldName, getJavaType(value));
        }
    }

    /**
     * 生成嵌套类名
     *
     * @param fieldName 字段名称
     * @return {@link String }
     */
    private String generateNestedClassName(final String fieldName) {
        return toUpperCamelCase(fieldName);
    }

    /**
     * 名称转为驼峰
     * @param name 名称
     * @return {@link String }
     */
    private String toUpperCamelCase(final String name) {
        if (StrUtil.isEmpty(name)) {
            return name;
        }
        // 使用正则表达式分割单词边界：非字母数字符或驼峰大写位置
        return Arrays.stream(name.split("(?<=\\W)|(?=\\p{Lu})"))
                .filter(StrUtil::isNotEmpty)
                .map(word -> {
                    // 忽略纯符号
                    if (word.matches("\\W+")) {
                        return "";
                    }
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
    private String getJavaType(final Object value) {
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
     * 生成记录类代码
     *
     * @param root 根
     * @return {@link String }
     */
    private String generateRecordCode(final ClassStructure root) {
        final StringBuilder code = new StringBuilder();
        buildRecordCode(root, code, 0);
        return code.toString().trim();
    }

    /**
     * 构建记录类代码
     *
     * @param clazz 类结构
     * @param code 代码
     * @param depth 深度
     */
    private void buildRecordCode(final ClassStructure clazz, final StringBuilder code, final int depth) {
        final String indent = "    ".repeat(depth);
        code.append(indent).append("public record ").append(clazz.className).append("(\n");
        IntStream.range(0, clazz.fields.size()).forEach(i -> {
            final Field field = clazz.fields.get(i);
            code.append(indent).append("    ").append(field.type).append(" ").append(field.name);
            code.append(i < clazz.fields.size() - 1 ? ",\n" : "\n");
        });
        code.append(indent).append(") {\n");
        clazz.nestedClasses.forEach(nested -> buildRecordCode(nested, code, depth + 1));
        code.append(indent).append("}\n");
    }

    /**
     * 生成类代码
     *
     * @param root 根
     * @return {@link String }
     */
    private String generateClassCode(final ClassStructure root) {
        final StringBuilder code = new StringBuilder();
        code.append("import lombok.Data;\n\n");
        buildClassCode(root, code, 0);
        return code.toString().trim();
    }

    /**
     * 构建类代码
     *
     * @param clazz 克拉兹
     * @param code 代码
     * @param depth 深度
     */
    @SuppressWarnings("SpellCheckingInspection")
    private void buildClassCode(final ClassStructure clazz, final StringBuilder code, final int depth) {
        final String indent = "    ".repeat(depth);
        code.append("@Data\n");
        code.append(clazz.fields.stream()
                .filter(Objects::nonNull)
                .map(field -> "%s    private %s %s;\n".formatted(indent, field.type, field.name))
                .collect(Collectors.joining("", "%spublic %sclass %s {\n".formatted(indent, depth > 0 ? "static " : "", clazz.className), "")));
        clazz.nestedClasses.forEach(nested -> buildClassCode(nested, code, depth + 1));
        code.append(indent).append("}\n");
    }

    /**
     * 类结构
     * @author 拒绝者
     * @date 2025-01-26
     */
    private static class ClassStructure {
        private final String className;
        private final List<Field> fields = new ArrayList<>();
        private final List<ClassStructure> nestedClasses = new ArrayList<>();

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