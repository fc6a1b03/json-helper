package com.acme.json.helper.common;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.intellij.psi.PsiClass;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.*;

/**
 * 时间类型处理
 * @author 拒绝者
 * @date 2025-01-25
 */
public class TemporalTypeHandler {
    /**
     * 支持处理的时间类型集合
     */
    private static final Set<String> TEMPORAL_TYPES = new HashSet<>();
    /**
     * 时间类型与格式化器的映射
     */
    private static final Map<String, DateTimeFormatter> FORMATTERS = new HashMap<>();

    static {
        final String formatConfig = """
                # 时间类型格式化配置（格式：`ClassName`=`BuiltinFormat.Pattern`）
                java.util.Date=NORM_DATETIME_PATTERN
                java.time.LocalTime=NORM_TIME_PATTERN
                java.time.LocalDate=NORM_DATE_PATTERN
                java.time.LocalDateTime=NORM_DATETIME_PATTERN
                java.time.OffsetDateTime=ISO_OFFSET_DATE_TIME
                java.time.ZonedDateTime=ISO_ZONED_DATE_TIME
                java.time.Instant=ISO_INSTANT
                """;
        new Scanner(formatConfig)
                .useDelimiter("\\R").tokens()
                .filter(StrUtil::isNotEmpty)
                .filter(line -> Boolean.FALSE.equals(line.startsWith("#")))
                .map(line -> line.split("=", 2))
                .forEach(parts -> {
                    final String className = StrUtil.trim(parts[0]);
                    FORMATTERS.put(className, BuiltinFormat.parseBuiltinFormat(StrUtil.trim(parts[1])));
                    TEMPORAL_TYPES.add(className);
                });
    }

    /**
     * 判断是否为支持处理的时间类型
     * @param psiClass PSI类
     * @return boolean
     */
    public static boolean isTemporal(final PsiClass psiClass) {
        return Opt.ofNullable(psiClass)
                .map(PsiClass::getQualifiedName)
                .filter(TEMPORAL_TYPES::contains)
                .isPresent();
    }

    /**
     * 格式化当前时间为指定类型的字符串表示
     * @param temporalClass 时态类
     * @return {@link String }
     */
    public static String format(final PsiClass temporalClass) {
        return Opt.ofNullable(temporalClass)
                .map(PsiClass::getQualifiedName)
                .map(className -> {
                    final Temporal temporal = createTemporalInstance(className);
                    final DateTimeFormatter formatter = FORMATTERS.get(className);
                    return Objects.nonNull(formatter) && Objects.nonNull(temporal) ? formatter.format(temporal) : "";
                })
                .orElse("");
    }

    /**
     * 创建对应时间类型的当前实例
     * @param className 类名
     * @return Temporal
     */
    private static Temporal createTemporalInstance(final String className) {
        return switch (className) {
            case "java.util.Date" -> ZonedDateTime.now().toInstant();
            case "java.time.LocalTime" -> LocalTime.now();
            case "java.time.LocalDate" -> LocalDate.now();
            case "java.time.LocalDateTime" -> LocalDateTime.now();
            case "java.time.OffsetDateTime" -> OffsetDateTime.now();
            case "java.time.ZonedDateTime" -> ZonedDateTime.now();
            case "java.time.Instant" -> Instant.now();
            default -> null;
        };
    }

    /**
     * 内置格式枚举（通过组合模式包装 DateTimeFormatter）
     * @author 拒绝者
     * @date 2025-01-25
     */
    private enum BuiltinFormat {
        ISO_OFFSET_DATE_TIME(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        ISO_ZONED_DATE_TIME(DateTimeFormatter.ISO_ZONED_DATE_TIME),
        ISO_INSTANT(DateTimeFormatter.ISO_INSTANT),
        NORM_DATETIME_PATTERN(DatePattern.NORM_DATETIME_FORMATTER),
        NORM_TIME_PATTERN(DatePattern.NORM_TIME_FORMATTER),
        NORM_DATE_PATTERN(DatePattern.NORM_DATE_FORMATTER),
        ;
        private final DateTimeFormatter formatter;

        BuiltinFormat(final DateTimeFormatter formatter) {
            this.formatter = formatter;
        }

        /**
         * 解析内置格式
         * @param pattern 图案
         * @return {@link DateTimeFormatter }
         */
        private static DateTimeFormatter parseBuiltinFormat(final String pattern) {
            return BuiltinFormat.valueOf(pattern).formatter();
        }

        /**
         * 格式化程序
         * @return {@link DateTimeFormatter }
         */
        public DateTimeFormatter formatter() {
            return formatter;
        }
    }
}