package com.etc.controller;

import com.etc.common.ApiResponse;
import com.etc.entity.PassRecord;
import com.etc.service.HBasePassRecordService;
import com.etc.service.HBaseStatsService;
import com.etc.service.QueryService;
import com.etc.service.StatsReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/progressive")
@RequiredArgsConstructor
@Tag(name = "渐进式查询", description = "MySQL(热) + HBase(历史) 渐进式加载")
public class ProgressiveQueryController {

    private final QueryService queryService;
    private final HBasePassRecordService hbasePassRecordService;
    private final HBaseStatsService hbaseStatsService;
    private final StatsReadService statsReadService;

    @GetMapping("/records")
    @Operation(summary = "综合查询通行记录（渐进式加载）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryRecords(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String checkpointId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "mysql") String source,
            @RequestParam(required = false) String lastRowKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        long t0 = System.nanoTime();

        if ("hbase".equalsIgnoreCase(source)) {
            HBasePassRecordService.QueryResult r = hbasePassRecordService.query(
                    plateNumber, checkpointId, startTime, endTime, lastRowKey, size);
            
            // 判断是否有筛选条件
            boolean hasFilters = (plateNumber != null && !plateNumber.isBlank()) 
                    || (checkpointId != null && !checkpointId.isBlank());
            
            // 从 Redis 缓存获取统计数据
            long totalCount = -1;
            boolean statsCached = false;
            
            if (!hasFilters && startTime != null && endTime != null) {
                // 无筛选条件，尝试从缓存获取精确计数
                LocalDate startDate = startTime.toLocalDate();
                LocalDate endDate = endTime.toLocalDate();
                HBaseStatsService.StatsResult stats = hbaseStatsService.getFilteredStats(
                        startDate, endDate, null, null);
                totalCount = stats.count();
                statsCached = stats.isCached();
            } else if (hasFilters && startTime != null && endTime != null) {
                // 有收费站筛选，尝试获取筛选后的统计
                LocalDate startDate = startTime.toLocalDate();
                LocalDate endDate = endTime.toLocalDate();
                HBaseStatsService.StatsResult stats = hbaseStatsService.getFilteredStats(
                        startDate, endDate, checkpointId, null);
                totalCount = stats.count();
                statsCached = stats.isCached();
            }
            
            // 如果缓存未命中，使用组合总数
            if (totalCount < 0 && !hasFilters) {
                totalCount = statsReadService.getCombinedTotal();
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("source", "hbase");
            data.put("list", r.list());
            data.put("mysqlTotal", statsReadService.getMysql7dTotal());
            data.put("totalCount", totalCount);
            data.put("hasFilters", hasFilters);
            data.put("statsCached", statsCached);
            data.put("hasMoreHistory", r.hasMoreHistory());
            data.put("current", page);
            data.put("size", size);
            data.put("nextRowKey", r.nextRowKey());
            data.put("queryTimeMs", r.queryTimeMs());
            return ResponseEntity.ok(ApiResponse.success(data));
        }

        // 默认走 MySQL（7天热数据）
        Page<PassRecord> result = queryService.search(plateNumber, checkpointId, startTime, endTime, null, page, size);

        List<Map<String, Object>> list = result.getContent().stream()
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", r.getId());
                    map.put("plateNumber", r.getHp());
                    map.put("checkpointName", r.getKkmc());
                    map.put("checkpointId", r.getCheckpointId());
                    map.put("passTime", r.getGcsj());
                    map.put("direction", r.getFxlx());
                    map.put("vehicleType", r.getClppxh());
                    map.put("plateType", r.getHpzl());
                    map.put("district", r.getXzqhmc());
                    map.put("source", "mysql");
                    return map;
                })
                .toList();

        long ms = (System.nanoTime() - t0) / 1_000_000;

        Map<String, Object> data = new HashMap<>();
        data.put("source", "mysql");
        data.put("list", list);
        data.put("mysqlTotal", result.getTotalElements());
        data.put("totalCount", Math.max(statsReadService.getCombinedTotal(), result.getTotalElements()));
        data.put("hasMoreHistory", false);
        data.put("current", page);
        data.put("size", size);
        data.put("queryTimeMs", ms);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/records/by-plate")
    @Operation(summary = "按车牌查询通行记录（渐进式加载）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryByPlate(
            @RequestParam String plateNumber,
            @RequestParam(defaultValue = "mysql") String source,
            @RequestParam(required = false) String lastRowKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return queryRecords(plateNumber, null, null, null, source, lastRowKey, page, size);
    }

    @GetMapping("/records/by-checkpoint")
    @Operation(summary = "按卡口查询通行记录（渐进式加载）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryByCheckpoint(
            @RequestParam String checkpointId,
            @RequestParam(defaultValue = "mysql") String source,
            @RequestParam(required = false) String lastRowKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return queryRecords(null, checkpointId, null, null, source, lastRowKey, page, size);
    }
}
