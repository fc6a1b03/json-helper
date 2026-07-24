package com.acme.prism.common;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.lang.Opt;
import com.intellij.psi.PsiClass;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 时间类型处理
 *
 * @author 拒绝者
 * @date 2025-01-25
 */
public class TemporalTypeHandler {
    /**
     * 时间类型与格式化器的映射
     */
    private static final Map<String, DateTimeFormatter> FORMATTERS = Map.ofEntries(
            Map.entry("java.util.Date", BuiltinFormat.NORM_DATETIME_PATTERN.formatter()),
            Map.entry("java.time.LocalTime", BuiltinFormat.NORM_TIME_PATTERN.formatter()),
            Map.entry("java.time.LocalDate", BuiltinFormat.NORM_DATE_PATTERN.formatter()),
            Map.entry("java.time.LocalDateTime", BuiltinFormat.NORM_DATETIME_PATTERN.formatter()),
            Map.entry("java.time.OffsetDateTime", BuiltinFormat.ISO_OFFSET_DATE_TIME.formatter()),
            Map.entry("java.time.ZonedDateTime", BuiltinFormat.ISO_ZONED_DATE_TIME.formatter()),
            Map.entry("java.time.Instant", BuiltinFormat.ISO_INSTANT.formatter())
    );
    /**
     * 支持处理的时间类型集合
     */
    private static final Set<String> TEMPORAL_TYPES = FORMATTERS.keySet();

    /**
     * 判断是否为支持处理的时间类型
     *
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
     *
     * @param temporalClass 时态类
     * @return {@link String }
     */
    @SuppressWarnings("DataFlowIssue")
    public static String format(final PsiClass temporalClass) {
        return Opt.ofNullable(temporalClass)
                .map(PsiClass::getQualifiedName)
                .map(className -> {
                    final Temporal temporal = createTemporalInstance(className);
                    final DateTimeFormatter formatter = FORMATTERS.get(className);
                    return Objects.nonNull(formatter) && Objects.nonNull(temporal) ? formatter.format(temporal) : "";
                }).orElse("");
    }

    /**
     * 创建对应时间类型的当前实例
     *
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
     *
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
         * 格式化程序
         *
         * @return {@link DateTimeFormatter }
         */
        public DateTimeFormatter formatter() {
            return formatter;
        }
    }
}
