package com.etc.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 计数器服务
 * 
 * 从 Redis 读取由 Flink CounterJob 维护的计数器，解决 HBase COUNT 性能问题
 * 
 * Key 设计：
 * - plate:count:{plate}           - 车牌全量历史通行次数
 * - plate:count:today:{date}:{plate} - 车牌当日通行次数
 * - cp:count:{cpId}               - 卡口全量历史通行次数
 * - cp:count:today:{date}:{cpId}  - 卡口当日通行次数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCounterService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Key 前缀
    private static final String PREFIX_PLATE_COUNT = "plate:count:";
    private static final String PREFIX_PLATE_TODAY = "plate:count:today:";
    private static final String PREFIX_CP_COUNT = "cp:count:";
    private static final String PREFIX_CP_TODAY = "cp:count:today:";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== 车牌计数 ====================

    /**
     * 获取车牌历史总通行次数
     */
    public long getPlateCount(String plateNumber) {
        String key = PREFIX_PLATE_COUNT + plateNumber;
        Object val = redisTemplate.opsForValue().get(key);
        return parseCount(val);
    }

    /**
     * 获取车牌今日通行次数
     */
    public long getPlateTodayCount(String plateNumber) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = PREFIX_PLATE_TODAY + date + ":" + plateNumber;
        Object val = redisTemplate.opsForValue().get(key);
        return parseCount(val);
    }

    /**
     * 获取车牌指定日期通行次数
     */
    public long getPlateDateCount(String plateNumber, LocalDate date) {
        String key = PREFIX_PLATE_TODAY + date.format(DATE_FMT) + ":" + plateNumber;
        Object val = redisTemplate.opsForValue().get(key);
        return parseCount(val);
    }

    /**
     * 批量获取多个车牌的历史总通行次数
     */
    public Map<String, Long> getPlateCounts(Collection<String> plateNumbers) {
        Map<String, Long> result = new HashMap<>();
        for (String plate : plateNumbers) {
            result.put(plate, getPlateCount(plate));
        }
        return result;
    }

    // ==================== 卡口计数 ====================

    /**
     * 获取卡口历史总通行次数
     */
    public long getCheckpointCount(String cpId) {
        String key = PREFIX_CP_COUNT + cpId;
        Object val = redisTemplate.opsForValue().get(key);
        return parseCount(val);
    }

    /**
     * 获取卡口今日通行次数
     */
    public long getCheckpointTodayCount(String cpId) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = PREFIX_CP_TODAY + date + ":" + cpId;
        Object val = redisTemplate.opsForValue().get(key);
        return parseCount(val);
    }

    /**
     * 获取卡口指定日期通行次数
     */
    public long getCheckpointDateCount(String cpId, LocalDate date) {
        String key = PREFIX_CP_TODAY + date.format(DATE_FMT) + ":" + cpId;
        Object val = redisTemplate.opsForValue().get(key);
        return parseCount(val);
    }

    /**
     * 批量获取所有卡口的历史总通行次数
     */
    public Map<String, Long> getAllCheckpointCounts() {
        Map<String, Long> result = new HashMap<>();
        Set<String> keys = redisTemplate.keys(PREFIX_CP_COUNT + "*");
        if (keys != null) {
            for (String key : keys) {
                // 排除带日期的 key
                if (!key.contains("today:")) {
                    String cpId = key.substring(PREFIX_CP_COUNT.length());
                    Object val = redisTemplate.opsForValue().get(key);
                    result.put(cpId, parseCount(val));
                }
            }
        }
        return result;
    }

    // ==================== 全局统计 ====================

    /**
     * 获取今日全局通行总量
     * 通过扫描所有 cp:count:today:{date}:* 求和
     */
    public long getTodayTotalCount() {
        String date = LocalDate.now().format(DATE_FMT);
        String pattern = PREFIX_CP_TODAY + date + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }

        long total = 0;
        for (String key : keys) {
            Object val = redisTemplate.opsForValue().get(key);
            total += parseCount(val);
        }
        return total;
    }

    /**
     * 获取全局历史通行总量（通过 global:total:count）
     */
    public long getGlobalTotalCount() {
        Object val = redisTemplate.opsForValue().get("global:total:count");
        return parseCount(val);
    }

    // ==================== 手动增量（备用） ====================

    /**
     * 增加车牌计数（备用手动方法，正常由 Flink 维护）
     */
    public void incrementPlateCount(String plateNumber) {
        String key = PREFIX_PLATE_COUNT + plateNumber;
        redisTemplate.opsForValue().increment(key, 1);

        String date = LocalDate.now().format(DATE_FMT);
        String todayKey = PREFIX_PLATE_TODAY + date + ":" + plateNumber;
        redisTemplate.opsForValue().increment(todayKey, 1);
        // 设置过期时间：2天后过期
        redisTemplate.expire(todayKey, 2, TimeUnit.DAYS);
    }

    /**
     * 增加卡口计数（备用手动方法，正常由 Flink 维护）
     */
    public void incrementCheckpointCount(String cpId) {
        String key = PREFIX_CP_COUNT + cpId;
        redisTemplate.opsForValue().increment(key, 1);

        String date = LocalDate.now().format(DATE_FMT);
        String todayKey = PREFIX_CP_TODAY + date + ":" + cpId;
        redisTemplate.opsForValue().increment(todayKey, 1);
        // 设置过期时间：2天后过期
        redisTemplate.expire(todayKey, 2, TimeUnit.DAYS);
    }

    // ==================== 工具方法 ====================

    private long parseCount(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            log.warn("无法解析计数值: {}", val);
            return 0L;
        }
    }
}
