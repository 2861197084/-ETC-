package com.etc.service;

import com.etc.common.HBaseTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.hadoop.hbase.CompareOperator.*;

/**
 * HBase 统计缓存服务
 * <p>
 * 功能：
 * 1. 预计算 HBase 按日期的总记录数并缓存到 Redis
 * 2. 预计算按日期+收费站的统计并缓存
 * 3. 支持按条件筛选时的近似计数
 * <p>
 * 这样前端查询时可以直接从 Redis 获取统计数据，避免每次都扫描 HBase
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HBaseStatsService {

    private static final byte[] CF = Bytes.toBytes("d");
    private static final String REDIS_PREFIX = "etc:stats:hbase:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Connection connection;
    private final StringRedisTemplate redis;

    @Value("${HBASE_TABLE:etc:pass_record}")
    private String tableName;

    @Value("${etc.hbase.stats.ttl-hours:24}")
    private int statsTtlHours;

    // 内存缓存用于快速访问
    private final Map<String, Long> dailyCountCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> dailyCheckpointCountCache = new ConcurrentHashMap<>();

    /**
     * 获取 HBase 按日期范围的总记录数（从 Redis 缓存）
     */
    public long getTotalCountForDateRange(LocalDate startDate, LocalDate endDate) {
        long total = 0;
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            total += getDailyCount(date);
            date = date.plusDays(1);
        }
        return total;
    }

    /**
     * 获取缓存中某一天的总记录数（不触发计算）
     */
    public Long getCachedTotalForDate(LocalDate date) {
        String dateStr = date.format(DATE_FMT);
        
        // 先查内存缓存
        Long cached = dailyCountCache.get(dateStr);
        if (cached != null) {
            return cached;
        }
        
        // 再查 Redis
        String key = REDIS_PREFIX + "daily:" + dateStr;
        String value = redis.opsForValue().get(key);
        if (value != null) {
            long count = Long.parseLong(value);
            dailyCountCache.put(dateStr, count);
            return count;
        }
        
        return null;
    }

    /**
     * 获取缓存中某一天各收费站的统计（不触发计算）
     */
    public Map<String, Long> getCachedCheckpointStats(LocalDate date) {
        String dateStr = date.format(DATE_FMT);
        
        // 先查内存缓存
        Map<String, Long> cached = dailyCheckpointCountCache.get(dateStr);
        if (cached != null && !cached.isEmpty()) {
            return new HashMap<>(cached);
        }
        
        // 再查 Redis
        String key = REDIS_PREFIX + "daily:checkpoint:" + dateStr;
        Map<Object, Object> redisData = redis.opsForHash().entries(key);
        if (!redisData.isEmpty()) {
            Map<String, Long> result = new HashMap<>();
            redisData.forEach((k, v) -> result.put(k.toString(), Long.parseLong(v.toString())));
            dailyCheckpointCountCache.put(dateStr, new ConcurrentHashMap<>(result));
            return result;
        }
        
        return null;
    }

    /**
     * 获取某一天的总记录数
     */
    public long getDailyCount(LocalDate date) {
        String dateStr = date.format(DATE_FMT);
        
        // 先查内存缓存
        Long cached = dailyCountCache.get(dateStr);
        if (cached != null) {
            return cached;
        }
        
        // 再查 Redis
        String key = REDIS_PREFIX + "daily:" + dateStr;
        String value = redis.opsForValue().get(key);
        if (value != null) {
            long count = Long.parseLong(value);
            dailyCountCache.put(dateStr, count);
            return count;
        }
        
        // Redis 没有，返回 -1 表示需要异步计算
        // 触发异步计算
        asyncComputeDailyStats(date);
        return -1;
    }

    /**
     * 获取某一天某收费站的记录数
     */
    public long getDailyCheckpointCount(LocalDate date, String checkpointId) {
        String dateStr = date.format(DATE_FMT);
        
        // 先查内存缓存
        Map<String, Long> checkpointCounts = dailyCheckpointCountCache.get(dateStr);
        if (checkpointCounts != null && checkpointCounts.containsKey(checkpointId)) {
            return checkpointCounts.get(checkpointId);
        }
        
        // 再查 Redis
        String key = REDIS_PREFIX + "daily:checkpoint:" + dateStr;
        Object value = redis.opsForHash().get(key, checkpointId);
        if (value != null) {
            long count = Long.parseLong(value.toString());
            dailyCheckpointCountCache
                .computeIfAbsent(dateStr, k -> new ConcurrentHashMap<>())
                .put(checkpointId, count);
            return count;
        }
        
        return -1;
    }

    /**
     * 获取按条件筛选的近似计数
     * 适用于有筛选条件时的估算
     */
    public StatsResult getFilteredStats(LocalDate startDate, LocalDate endDate, 
                                        String checkpointId, String direction) {
        boolean hasCheckpoint = checkpointId != null && !checkpointId.isBlank();
        boolean hasDirection = direction != null && !direction.isBlank();
        
        // 没有筛选条件，直接返回日期范围总数
        if (!hasCheckpoint && !hasDirection) {
            long total = getTotalCountForDateRange(startDate, endDate);
            return new StatsResult(total, total >= 0, false);
        }
        
        // 有收费站筛选，尝试从缓存获取
        if (hasCheckpoint && !hasDirection) {
            long total = 0;
            boolean allCached = true;
            LocalDate date = startDate;
            while (!date.isAfter(endDate)) {
                long count = getDailyCheckpointCount(date, checkpointId.trim());
                if (count < 0) {
                    allCached = false;
                    break;
                }
                total += count;
                date = date.plusDays(1);
            }
            if (allCached) {
                return new StatsResult(total, true, false);
            }
        }
        
        // 缓存不完整或有方向筛选，返回估算值
        return new StatsResult(-1, false, true);
    }

    /**
     * 异步计算某日统计
     */
    private void asyncComputeDailyStats(LocalDate date) {
        CompletableFuture.runAsync(() -> {
            try {
                computeAndCacheDailyStats(date);
            } catch (Exception e) {
                log.warn("Failed to compute daily stats for {}: {}", date, e.getMessage());
            }
        });
    }

    /**
     * 计算并缓存某一天的统计
     */
    public void computeAndCacheDailyStats(LocalDate date) {
        String dateStr = date.format(DATE_FMT);
        log.info("Computing HBase stats for date: {}", dateStr);
        
        long startTime = System.currentTimeMillis();
        
        // 使用 HBaseTimeUtils 获取时间范围（兼容多种格式）
        HBaseTimeUtils.TimeRange timeRange = HBaseTimeUtils.getDayRange(date);
        
        log.debug("Scanning HBase with time range: min={} to max={}", timeRange.minStart(), timeRange.maxEnd());
        
        Map<String, AtomicLong> checkpointCounts = new HashMap<>();
        AtomicLong totalCount = new AtomicLong(0);
        
        // 使用字典序最小和最大的时间字符串，确保覆盖所有可能的格式
        FilterList filters = new FilterList(FilterList.Operator.MUST_PASS_ALL);
        filters.addFilter(geFilter("gcsj", timeRange.minStart()));
        filters.addFilter(leFilter("gcsj", timeRange.maxEnd()));
        
        Scan scan = new Scan();
        scan.setFilter(filters);
        scan.addColumn(CF, Bytes.toBytes("checkpoint_id"));
        scan.addColumn(CF, Bytes.toBytes("gcsj")); // 需要读取时间字段进行二次验证
        scan.setCaching(500);
        
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay().minusSeconds(1);
        
        try (Table table = connection.getTable(TableName.valueOf(tableName));
             ResultScanner scanner = table.getScanner(scan)) {
            
            for (Result r : scanner) {
                if (r == null || r.isEmpty()) continue;
                
                // 二次验证：解析时间字段确保在当天范围内
                byte[] gcsjBytes = r.getValue(CF, Bytes.toBytes("gcsj"));
                if (gcsjBytes != null) {
                    String gcsjStr = Bytes.toString(gcsjBytes);
                    if (!HBaseTimeUtils.isInRange(gcsjStr, dayStart, dayEnd)) {
                        continue; // 不在当天范围内，跳过
                    }
                }
                
                totalCount.incrementAndGet();
                
                byte[] cpBytes = r.getValue(CF, Bytes.toBytes("checkpoint_id"));
                if (cpBytes != null) {
                    String cp = Bytes.toString(cpBytes);
                    checkpointCounts.computeIfAbsent(cp, k -> new AtomicLong(0)).incrementAndGet();
                }
            }
        } catch (Exception e) {
            log.error("Error computing daily stats for {}: {}", dateStr, e.getMessage());
            return;
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Computed HBase stats for {}: total={}, checkpoints={}, elapsed={}ms",
                dateStr, totalCount.get(), checkpointCounts.size(), elapsed);
        
        // 存入 Redis
        Duration ttl = Duration.ofHours(statsTtlHours);
        String dailyKey = REDIS_PREFIX + "daily:" + dateStr;
        redis.opsForValue().set(dailyKey, String.valueOf(totalCount.get()), ttl);
        
        String cpKey = REDIS_PREFIX + "daily:checkpoint:" + dateStr;
        redis.delete(cpKey);
        for (Map.Entry<String, AtomicLong> entry : checkpointCounts.entrySet()) {
            redis.opsForHash().put(cpKey, entry.getKey(), String.valueOf(entry.getValue().get()));
        }
        redis.expire(cpKey, ttl);
        
        // 更新内存缓存
        dailyCountCache.put(dateStr, totalCount.get());
        Map<String, Long> cpMap = new ConcurrentHashMap<>();
        checkpointCounts.forEach((k, v) -> cpMap.put(k, v.get()));
        dailyCheckpointCountCache.put(dateStr, cpMap);
    }

    /**
     * 启动时预热 2023-12 月份的统计缓存
     */
    @PostConstruct
    public void warmUpCache() {
        CompletableFuture.runAsync(() -> {
            try {
                // 检查是否已有缓存
                Set<String> keys = redis.keys(REDIS_PREFIX + "daily:2023-12-*");
                if (keys != null && keys.size() >= 31) {
                    log.info("HBase daily stats cache already warmed up ({} days)", keys.size());
                    // 加载到内存缓存
                    for (String key : keys) {
                        String dateStr = key.replace(REDIS_PREFIX + "daily:", "");
                        String value = redis.opsForValue().get(key);
                        if (value != null) {
                            dailyCountCache.put(dateStr, Long.parseLong(value));
                        }
                    }
                    return;
                }
                
                log.info("Starting HBase stats cache warm-up for 2023-12...");
                LocalDate start = LocalDate.of(2023, 12, 1);
                LocalDate end = LocalDate.of(2023, 12, 31);
                
                LocalDate date = start;
                while (!date.isAfter(end)) {
                    computeAndCacheDailyStats(date);
                    date = date.plusDays(1);
                }
                log.info("HBase stats cache warm-up completed");
            } catch (Exception e) {
                log.warn("Failed to warm up HBase stats cache: {}", e.getMessage());
            }
        });
    }

    /**
     * 每天凌晨刷新缓存
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void refreshCache() {
        log.info("Refreshing HBase stats cache...");
        dailyCountCache.clear();
        dailyCheckpointCountCache.clear();
        warmUpCache();
    }

    private static SingleColumnValueFilter geFilter(String qualifier, String value) {
        SingleColumnValueFilter f = new SingleColumnValueFilter(
                CF,
                Bytes.toBytes(qualifier),
                GREATER_OR_EQUAL,
                new BinaryComparator(value.getBytes(StandardCharsets.UTF_8)));
        f.setFilterIfMissing(true);
        return f;
    }

    private static SingleColumnValueFilter leFilter(String qualifier, String value) {
        SingleColumnValueFilter f = new SingleColumnValueFilter(
                CF,
                Bytes.toBytes(qualifier),
                LESS_OR_EQUAL,
                new BinaryComparator(value.getBytes(StandardCharsets.UTF_8)));
        f.setFilterIfMissing(true);
        return f;
    }

    public record StatsResult(long count, boolean isCached, boolean isEstimated) {}
}
