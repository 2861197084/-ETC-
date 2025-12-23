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
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "mysql") String source,
            @RequestParam(required = false) String lastRowKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        long t0 = System.nanoTime();

        if ("hbase".equalsIgnoreCase(source)) {
            // 判断是否为"情况1：纯时间范围查询"（无车牌/卡口/方向筛选）
            boolean isTimeOnlyQuery = (plateNumber == null || plateNumber.isBlank())
                    && (checkpointId == null || checkpointId.isBlank())
                    && (direction == null || direction.isBlank())
                    && startTime != null && endTime != null;
            
            HBasePassRecordService.QueryResult r;
            if (isTimeOnlyQuery) {
                // 情况1：使用并行扫描 + K路归并
                r = hbasePassRecordService.queryParallel(startTime, endTime, lastRowKey, size);
            } else {
                // 其他情况：使用原有顺序扫描
                r = hbasePassRecordService.query(plateNumber, checkpointId, direction, startTime, endTime, lastRowKey, page, size);
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("source", "hbase");
            data.put("list", r.list());
            data.put("total", r.total());
            data.put("page", page);
            data.put("size", size);
            data.put("hasMoreHistory", r.hasMoreHistory());
            data.put("nextRowKey", r.nextRowKey());
            data.put("queryTimeMs", r.queryTimeMs());
            return ResponseEntity.ok(ApiResponse.success(data));
        }

        // 默认走 MySQL（7天热数据）
        Page<PassRecord> result = queryService.search(plateNumber, checkpointId, startTime, endTime, direction, page, size);

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
        return queryRecords(plateNumber, null, null, null, null, source, lastRowKey, page, size);
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
        return queryRecords(null, checkpointId, null, null, null, source, lastRowKey, page, size);
    }
}
