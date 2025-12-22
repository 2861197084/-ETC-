package com.etc.controller;

import com.etc.common.ApiResponse;
import com.etc.service.HBaseStatsService;
import com.etc.service.StatsReadService;
import com.etc.service.TrinoQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一统计查询接口
 * <p>
 * 结合 Redis 缓存 + Trino 联邦查询，提供高效的统计服务
 */
@Slf4j
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "统计查询", description = "统一统计查询接口（Redis缓存 + Trino联邦查询）")
public class StatsQueryController {

    private final HBaseStatsService hbaseStatsService;
    private final StatsReadService statsReadService;
    private final TrinoQueryService trinoQueryService;

    @GetMapping("/total")
    @Operation(summary = "获取总记录数统计", description = "根据日期范围获取记录总数，自动选择数据源")
    public ApiResponse<Map<String, Object>> getTotalStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String checkpointId) {
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        
        LocalDate cutoffDate = LocalDate.of(2024, 1, 1);
        
        long hbaseCount = 0;
        long mysqlCount = 0;
        boolean hbaseCached = false;
        boolean mysqlCached = false;
        String source = "";
        Map<String, Long> checkpointCounts = new HashMap<>();
        
        // HBase 历史数据（2024-01-01 之前）
        if (startDate.isBefore(cutoffDate)) {
            LocalDate hbaseEnd = endDate.isBefore(cutoffDate) ? endDate : cutoffDate.minusDays(1);
            
            if (checkpointId != null && !checkpointId.isBlank()) {
                HBaseStatsService.StatsResult stats = hbaseStatsService.getFilteredStats(
                        startDate, hbaseEnd, checkpointId, null);
                hbaseCount = stats.count();
                hbaseCached = stats.isCached();
                // 单卡口筛选，只放该卡口统计
                if (hbaseCount > 0) {
                    checkpointCounts.put(checkpointId, hbaseCount);
                }
            } else {
                hbaseCount = hbaseStatsService.getTotalCountForDateRange(startDate, hbaseEnd);
                hbaseCached = hbaseCount >= 0;
                // 获取各卡口统计
                LocalDate date = startDate;
                while (!date.isAfter(hbaseEnd)) {
                    Map<String, Long> dailyStats = hbaseStatsService.getCachedCheckpointStats(date);
                    if (dailyStats != null) {
                        dailyStats.forEach((cp, count) -> 
                            checkpointCounts.merge(cp, count, Long::sum)
                        );
                    }
                    date = date.plusDays(1);
                }
            }
            source = "hbase";
        }
        
        // MySQL 热数据（2024-01-01 之后）
        if (!endDate.isBefore(cutoffDate)) {
            LocalDate mysqlStart = startDate.isBefore(cutoffDate) ? cutoffDate : startDate;
            
            // 尝试用 Redis 缓存
            Long cached7d = statsReadService.getMysql7dTotal();
            if (cached7d != null && cached7d > 0) {
                mysqlCount = cached7d;
                mysqlCached = true;
            } else if (trinoQueryService.isAvailable()) {
                // 回退到 Trino 联邦查询
                LocalDateTime start = mysqlStart.atStartOfDay();
                LocalDateTime end = endDate.atTime(23, 59, 59);
                mysqlCount = trinoQueryService.getMySqlTotalCount(start, end);
            }
            source = source.isEmpty() ? "mysql" : source + "+mysql";
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        result.put("hbaseCount", hbaseCount);
        result.put("mysqlCount", mysqlCount);
        result.put("totalCount", Math.max(0, hbaseCount) + Math.max(0, mysqlCount));
        result.put("hbaseCached", hbaseCached);
        result.put("mysqlCached", mysqlCached);
        result.put("checkpointCounts", checkpointCounts);
        result.put("source", source);
        result.put("queryTimeMs", elapsed);
        
        return ApiResponse.success(result);
    }

    @GetMapping("/daily")
    @Operation(summary = "获取每日统计", description = "获取日期范围内每天的记录数")
    public ApiResponse<Map<String, Object>> getDailyStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        Map<String, Long> dailyData = new HashMap<>();
        
        LocalDate cutoffDate = LocalDate.of(2024, 1, 1);
        
        // HBase 历史数据
        if (startDate.isBefore(cutoffDate)) {
            LocalDate hbaseEnd = endDate.isBefore(cutoffDate) ? endDate : cutoffDate.minusDays(1);
            LocalDate date = startDate;
            while (!date.isAfter(hbaseEnd)) {
                long count = hbaseStatsService.getDailyCount(date);
                if (count >= 0) {
                    dailyData.put(date.toString(), count);
                }
                date = date.plusDays(1);
            }
        }
        
        // MySQL 热数据 - 使用 Trino
        if (!endDate.isBefore(cutoffDate) && trinoQueryService.isAvailable()) {
            LocalDate mysqlStart = startDate.isBefore(cutoffDate) ? cutoffDate : startDate;
            Map<String, Long> mysqlDaily = trinoQueryService.getDailyStats(mysqlStart, endDate);
            dailyData.putAll(mysqlDaily);
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        result.put("dailyData", dailyData);
        result.put("queryTimeMs", elapsed);
        
        return ApiResponse.success(result);
    }

    @GetMapping("/by-checkpoint")
    @Operation(summary = "获取按收费站统计", description = "获取日期范围内按收费站分组的记录数")
    public ApiResponse<Map<String, Object>> getStatsByCheckpoint(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        Map<String, Long> checkpointData = new HashMap<>();
        
        LocalDate cutoffDate = LocalDate.of(2024, 1, 1);
        
        // MySQL 热数据 - 使用 Trino
        if (!endDate.isBefore(cutoffDate) && trinoQueryService.isAvailable()) {
            LocalDate mysqlStart = startDate.isBefore(cutoffDate) ? cutoffDate : startDate;
            LocalDateTime start = mysqlStart.atStartOfDay();
            LocalDateTime end = endDate.atTime(23, 59, 59);
            checkpointData = trinoQueryService.getMySqlCountByCheckpoint(start, end);
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        result.put("checkpointData", checkpointData);
        result.put("queryTimeMs", elapsed);
        
        return ApiResponse.success(result);
    }

    @GetMapping("/trino/status")
    @Operation(summary = "Trino 状态检查", description = "检查 Trino 服务是否可用")
    public ApiResponse<Map<String, Object>> getTrinoStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("available", trinoQueryService.isAvailable());
        return ApiResponse.success(result);
    }

    @PostMapping("/trino/query")
    @Operation(summary = "执行 Trino 查询", description = "执行自定义 Trino SQL 查询（仅限开发调试）")
    public ApiResponse<List<Map<String, Object>>> executeTrinoQuery(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        if (sql == null || sql.isBlank()) {
            return ApiResponse.error(400, "SQL is required");
        }
        
        try {
            List<Map<String, Object>> results = trinoQueryService.executeQuery(sql);
            return ApiResponse.success(results);
        } catch (Exception e) {
            return ApiResponse.error(500, "Query failed: " + e.getMessage());
        }
    }

    @PostMapping("/hbase/refresh")
    @Operation(summary = "刷新 HBase 统计缓存", description = "手动触发 HBase 统计缓存刷新")
    public ApiResponse<String> refreshHBaseCache() {
        hbaseStatsService.refreshCache();
        return ApiResponse.success("Cache refresh triggered");
    }

    @GetMapping("/hbase/warmup")
    @Operation(summary = "预热单天 HBase 统计", description = "手动计算并缓存指定日期的 HBase 统计")
    public ApiResponse<Map<String, Object>> warmupHBaseStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        long startTime = System.currentTimeMillis();
        
        // 计算并缓存指定日期的统计
        hbaseStatsService.computeAndCacheDailyStats(date);
        
        // 从缓存读取结果
        Long cachedTotal = hbaseStatsService.getCachedTotalForDate(date);
        Map<String, Long> checkpointStats = hbaseStatsService.getCachedCheckpointStats(date);
        
        Map<String, Object> result = new HashMap<>();
        result.put("date", date.toString());
        result.put("total", cachedTotal != null ? cachedTotal : 0);
        result.put("checkpointCount", checkpointStats != null ? checkpointStats.size() : 0);
        result.put("checkpoints", checkpointStats);
        result.put("queryTimeMs", System.currentTimeMillis() - startTime);
        
        return ApiResponse.success(result);
    }

    @GetMapping("/hbase/cached")
    @Operation(summary = "获取缓存的 HBase 统计", description = "获取已缓存的指定日期统计（不重新计算）")
    public ApiResponse<Map<String, Object>> getCachedHBaseStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Long cachedTotal = hbaseStatsService.getCachedTotalForDate(date);
        Map<String, Long> checkpointStats = hbaseStatsService.getCachedCheckpointStats(date);
        
        Map<String, Object> result = new HashMap<>();
        result.put("date", date.toString());
        result.put("cached", cachedTotal != null);
        result.put("total", cachedTotal != null ? cachedTotal : 0);
        result.put("checkpoints", checkpointStats);
        
        return ApiResponse.success(result);
    }
}
