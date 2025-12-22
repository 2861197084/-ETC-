package com.etc.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * HBase 时间格式工具类
 * <p>
 * 支持多种时间格式的解析和转换，解决数据不一致问题：
 * - 格式1: "yyyy/M/d H:mm:ss" (如 "2023/12/1 8:30:00")
 * - 格式2: "yyyy-MM-dd HH:mm:ss" (如 "2023-12-01 08:30:00")
 */
public final class HBaseTimeUtils {

    private HBaseTimeUtils() {}

    // 支持的时间格式列表（按优先级排序）
    private static final List<DateTimeFormatter> DATETIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),      // 2023/12/1 8:30:00
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"),     // 2023/12/1 08:30:00
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),   // 2023-12-01 08:30:00
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss"),    // 2023-12-01 8:30:00
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),   // 2023/12/01 08:30:00
            DateTimeFormatter.ofPattern("yyyy/MM/dd H:mm:ss")     // 2023/12/01 8:30:00
    );

    // 主要输出格式（用于查询条件）
    public static final DateTimeFormatter FORMAT_SLASH = DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss");
    public static final DateTimeFormatter FORMAT_DASH = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 解析时间字符串，自动识别格式
     */
    public static LocalDateTime parse(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        
        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(timeStr.trim(), formatter);
            } catch (DateTimeParseException e) {
                // 尝试下一个格式
            }
        }
        
        // 所有格式都失败
        throw new IllegalArgumentException("Cannot parse time: " + timeStr);
    }

    /**
     * 安全解析，失败返回 null
     */
    public static LocalDateTime parseSafe(String timeStr) {
        try {
            return parse(timeStr);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成日期范围的时间字符串（两种格式）
     * 用于 HBase 范围查询，同时覆盖两种可能的数据格式
     */
    public static TimeRange getDayRange(LocalDate date) {
        // 格式1: yyyy/M/d 风格
        String slashStart = date.getYear() + "/" + date.getMonthValue() + "/" + date.getDayOfMonth() + " 0:00:00";
        String slashEnd = date.getYear() + "/" + date.getMonthValue() + "/" + date.getDayOfMonth() + " 23:59:59";
        
        // 格式2: yyyy-MM-dd 风格
        String dashStart = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 00:00:00";
        String dashEnd = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 23:59:59";
        
        return new TimeRange(slashStart, slashEnd, dashStart, dashEnd);
    }

    /**
     * 将 LocalDateTime 转换为查询用的时间字符串（两种格式）
     */
    public static String[] toQueryStrings(LocalDateTime dateTime) {
        if (dateTime == null) {
            return new String[0];
        }
        return new String[] {
                dateTime.format(FORMAT_SLASH),
                dateTime.format(FORMAT_DASH)
        };
    }

    /**
     * 获取用于字典序比较的最小时间字符串
     * 由于 "/" 的 ASCII 码比 "-" 大，所以 "2023-" < "2023/"
     */
    public static String getMinTimeString(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        // yyyy-MM-dd 格式在字典序中更小
        return dateTime.format(FORMAT_DASH);
    }

    /**
     * 获取用于字典序比较的最大时间字符串
     */
    public static String getMaxTimeString(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        // yyyy/M/d 格式在字典序中更大（因为 "/" > "-"）
        return dateTime.format(FORMAT_SLASH);
    }

    /**
     * 检查时间字符串是否在指定范围内（支持多格式）
     */
    public static boolean isInRange(String timeStr, LocalDateTime start, LocalDateTime end) {
        LocalDateTime time = parseSafe(timeStr);
        if (time == null) return false;
        
        boolean afterStart = start == null || !time.isBefore(start);
        boolean beforeEnd = end == null || !time.isAfter(end);
        return afterStart && beforeEnd;
    }

    /**
     * 时间范围结构
     */
    public record TimeRange(
            String slashStart,  // yyyy/M/d 格式开始
            String slashEnd,    // yyyy/M/d 格式结束
            String dashStart,   // yyyy-MM-dd 格式开始
            String dashEnd      // yyyy-MM-dd 格式结束
    ) {
        /**
         * 获取字典序最小的开始时间
         */
        public String minStart() {
            return dashStart.compareTo(slashStart) < 0 ? dashStart : slashStart;
        }

        /**
         * 获取字典序最大的结束时间
         */
        public String maxEnd() {
            return dashEnd.compareTo(slashEnd) > 0 ? dashEnd : slashEnd;
        }
    }
}
