package com.etc.platform.controller;

import com.etc.platform.dto.ApiResponse;
import com.etc.platform.dto.ProgressiveLoadResult;
import com.etc.platform.entity.PassRecord;
import com.etc.platform.repository.PassRecordRepository;
import com.etc.platform.service.CheckpointService;
import com.etc.platform.service.HBaseHistoryService;
import com.etc.platform.service.HBaseHistoryService.HistoryQueryResult;
import com.etc.platform.service.RedisCounterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 渐进式加载 API
 * 
 * 实现 MySQL (7天热数据) + HBase (历史全量) 的两阶段查询策略：
 * 1. 首次查询：从 MySQL 获取最近7天数据，从 Redis 获取全量计数
 * 2. 加载更多：从 HBase 获取超过7天的历史数据
 * 
 * 前端展示策略：
 * - 首屏快速响应（MySQL 毫秒级）
 * - 展示"加载更多历史记录"按钮
 * - 用户点击后加载 HBase 数据
 */
@Slf4j
@RestController
@RequestMapping("/api/progressive")
@RequiredArgsConstructor
public class ProgressiveLoadController {

    private final PassRecordRepository passRecordRepository;
    private final CheckpointService checkpointService;
    private final RedisCounterService redisCounterService;
    private final HBaseHistoryService hbaseHistoryService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 按车牌查询通行记录（渐进式加载）
     * 
     * 首次调用：source=mysql，返回MySQL数据 + Redis计数
     * 加载更多：source=hbase，使用 lastRowKey 分页
     */
    @GetMapping("/records/by-plate")
    public ApiResponse<ProgressiveLoadResult<Map<String, Object>>> queryByPlate(
            @RequestParam String plateNumber,
            @RequestParam(defaultValue = "mysql") String source,
            @RequestParam(required = false) String lastRowKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        long startTime = System.currentTimeMillis();

        if ("hbase".equalsIgnoreCase(source)) {
            // 从 HBase 加载历史数据
            HistoryQueryResult hbaseResult = hbaseHistoryService.queryByPlate(plateNumber, lastRowKey, size);
            long totalCount = redisCounterService.getPlateCount(plateNumber);

            ProgressiveLoadResult<Map<String, Object>> result = ProgressiveLoadResult.fromHBase(
                hbaseResult.records(), totalCount, hbaseResult.nextRowKey(), page, size
            );
            result.setQueryTimeMs(System.currentTimeMillis() - startTime);
            return ApiResponse.ok(result);
        }

        // 默认从 MySQL 查询最近7天数据
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "passTime"));
        Page<PassRecord> mysqlPage = passRecordRepository.findByPlateNumberContaining(plateNumber, pageRequest);

        // 从 Redis 获取全量计数
        long totalCount = redisCounterService.getPlateCount(plateNumber);
        if (totalCount == 0) {
            // 如果 Redis 没有数据，使用 MySQL 计数
            totalCount = passRecordRepository.countByPlateNumberContaining(plateNumber);
        }

        // 转换数据格式
        Map<Long, String> cpNameMap = checkpointService.getCheckpointNameMap();
        List<Map<String, Object>> records = mysqlPage.getContent().stream()
                .map(r -> convertToMap(r, cpNameMap))
                .toList();

        ProgressiveLoadResult<Map<String, Object>> result = ProgressiveLoadResult.fromMysql(
            records, mysqlPage.getTotalElements(), totalCount, page, size
        );
        result.setQueryTimeMs(System.currentTimeMillis() - startTime);

        // 添加统计信息
        Map<String, Object> stats = new HashMap<>();
        stats.put("todayCount", redisCounterService.getPlateTodayCount(plateNumber));
        stats.put("hbaseConnected", hbaseHistoryService.isConnected());
        result.setStats(stats);

        return ApiResponse.ok(result);
    }

    /**
     * 按卡口查询通行记录（渐进式加载）
     */
    @GetMapping("/records/by-checkpoint")
    public ApiResponse<ProgressiveLoadResult<Map<String, Object>>> queryByCheckpoint(
            @RequestParam String checkpointId,
            @RequestParam(defaultValue = "mysql") String source,
            @RequestParam(required = false) String lastRowKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        long startTime = System.currentTimeMillis();
        Long cpIdLong = Long.parseLong(checkpointId);
        // 转换为数据库中的字符串格式 CP001
        String checkpointIdStr = "CP" + String.format("%03d", cpIdLong);

        if ("hbase".equalsIgnoreCase(source)) {
            HistoryQueryResult hbaseResult = hbaseHistoryService.queryByCheckpoint(checkpointId, lastRowKey, size);
            long totalCount = redisCounterService.getCheckpointCount(checkpointId);

            ProgressiveLoadResult<Map<String, Object>> result = ProgressiveLoadResult.fromHBase(
                hbaseResult.records(), totalCount, hbaseResult.nextRowKey(), page, size
            );
            result.setQueryTimeMs(System.currentTimeMillis() - startTime);
            return ApiResponse.ok(result);
        }

        // MySQL 查询
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "passTime"));
        Page<PassRecord> mysqlPage = passRecordRepository.findByCheckpointIdStr(checkpointIdStr, pageRequest);

        long totalCount = redisCounterService.getCheckpointCount(checkpointId);
        if (totalCount == 0) {
            totalCount = passRecordRepository.countByCheckpointIdStr(checkpointIdStr);
        }

        Map<Long, String> cpNameMap = checkpointService.getCheckpointNameMap();
        List<Map<String, Object>> records = mysqlPage.getContent().stream()
                .map(r -> convertToMap(r, cpNameMap))
                .toList();

        ProgressiveLoadResult<Map<String, Object>> result = ProgressiveLoadResult.fromMysql(
            records, mysqlPage.getTotalElements(), totalCount, page, size
        );
        result.setQueryTimeMs(System.currentTimeMillis() - startTime);

        Map<String, Object> stats = new HashMap<>();
        stats.put("todayCount", redisCounterService.getCheckpointTodayCount(checkpointId));
        stats.put("checkpointName", cpNameMap.get(cpIdLong));
        result.setStats(stats);

        return ApiResponse.ok(result);
    }

    /**
     * 综合查询通行记录（渐进式加载）
     */
    @GetMapping("/records")
    public ApiResponse<ProgressiveLoadResult<Map<String, Object>>> queryRecords(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String checkpointId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "mysql") String source,
            @RequestParam(required = false) String lastRowKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        long queryStartTime = System.currentTimeMillis();

        // 解析时间参数
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);

        if ("hbase".equalsIgnoreCase(source)) {
            // HBase 查询
            HistoryQueryResult hbaseResult = hbaseHistoryService.query(
                plateNumber, checkpointId, start, end, lastRowKey, size
            );
            
            // 获取计数
            long totalCount = 0;
            if (plateNumber != null && !plateNumber.isEmpty()) {
                totalCount = redisCounterService.getPlateCount(plateNumber);
            } else if (checkpointId != null && !checkpointId.isEmpty()) {
                totalCount = redisCounterService.getCheckpointCount(checkpointId);
            }

            ProgressiveLoadResult<Map<String, Object>> result = ProgressiveLoadResult.fromHBase(
                hbaseResult.records(), totalCount, hbaseResult.nextRowKey(), page, size
            );
            result.setQueryTimeMs(System.currentTimeMillis() - queryStartTime);
            return ApiResponse.ok(result);
        }

        // MySQL 查询
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "passTime"));
        Page<PassRecord> mysqlPage;

        if (plateNumber != null && !plateNumber.isEmpty()) {
            if (start != null && end != null) {
                mysqlPage = passRecordRepository.findByPlateNumberContainingAndPassTimeBetween(
                    plateNumber, start, end, pageRequest);
            } else {
                mysqlPage = passRecordRepository.findByPlateNumberContaining(plateNumber, pageRequest);
            }
        } else if (checkpointId != null && !checkpointId.isEmpty()) {
            // 转换为数据库中的字符串格式 CP001
            String cpIdStr = "CP" + String.format("%03d", Long.parseLong(checkpointId));
            if (start != null && end != null) {
                mysqlPage = passRecordRepository.findByCheckpointIdStrAndPassTimeBetween(
                    cpIdStr, start, end, pageRequest);
            } else {
                mysqlPage = passRecordRepository.findByCheckpointIdStr(cpIdStr, pageRequest);
            }
        } else if (start != null && end != null) {
            mysqlPage = passRecordRepository.findByPassTimeBetween(start, end, pageRequest);
        } else {
            mysqlPage = passRecordRepository.findAll(pageRequest);
        }

        // 获取计数
        long totalCount = mysqlPage.getTotalElements();
        if (plateNumber != null && !plateNumber.isEmpty()) {
            long redisCount = redisCounterService.getPlateCount(plateNumber);
            if (redisCount > 0) totalCount = redisCount;
        } else if (checkpointId != null && !checkpointId.isEmpty()) {
            long redisCount = redisCounterService.getCheckpointCount(checkpointId);
            if (redisCount > 0) totalCount = redisCount;
        }

        Map<Long, String> cpNameMap = checkpointService.getCheckpointNameMap();
        List<Map<String, Object>> records = mysqlPage.getContent().stream()
                .map(r -> convertToMap(r, cpNameMap))
                .toList();

        ProgressiveLoadResult<Map<String, Object>> result = ProgressiveLoadResult.fromMysql(
            records, mysqlPage.getTotalElements(), totalCount, page, size
        );
        result.setQueryTimeMs(System.currentTimeMillis() - queryStartTime);

        Map<String, Object> stats = new HashMap<>();
        stats.put("globalTodayCount", redisCounterService.getTodayTotalCount());
        stats.put("hbaseConnected", hbaseHistoryService.isConnected());
        result.setStats(stats);

        return ApiResponse.ok(result);
    }

    /**
     * 获取计数统计
     */
    @GetMapping("/count/plate/{plateNumber}")
    public ApiResponse<Map<String, Object>> getPlateCount(@PathVariable String plateNumber) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("plateNumber", plateNumber);
        stats.put("totalCount", redisCounterService.getPlateCount(plateNumber));
        stats.put("todayCount", redisCounterService.getPlateTodayCount(plateNumber));
        return ApiResponse.ok(stats);
    }

    @GetMapping("/count/checkpoint/{checkpointId}")
    public ApiResponse<Map<String, Object>> getCheckpointCount(@PathVariable String checkpointId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("checkpointId", checkpointId);
        stats.put("totalCount", redisCounterService.getCheckpointCount(checkpointId));
        stats.put("todayCount", redisCounterService.getCheckpointTodayCount(checkpointId));
        stats.put("checkpointName", checkpointService.getCheckpointNameMap().get(Long.parseLong(checkpointId)));
        return ApiResponse.ok(stats);
    }

    @GetMapping("/count/global")
    public ApiResponse<Map<String, Object>> getGlobalCount() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("todayCount", redisCounterService.getTodayTotalCount());
        stats.put("totalCount", redisCounterService.getGlobalTotalCount());
        stats.put("checkpointCounts", redisCounterService.getAllCheckpointCounts());
        return ApiResponse.ok(stats);
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> convertToMap(PassRecord record, Map<Long, String> cpNameMap) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("plateNumber", record.getPlateNumber());
        map.put("checkpointId", record.getCheckpointId());
        map.put("checkpointName", cpNameMap.getOrDefault(record.getCheckpointId(), "卡口" + record.getCheckpointId()));
        map.put("passTime", record.getPassTime() != null ? record.getPassTime().format(DT_FMT) : null);
        map.put("direction", record.getDirection());
        map.put("speed", record.getSpeed());
        map.put("laneNo", record.getLaneNo());
        map.put("vehicleType", record.getVehicleType());
        map.put("etcDeduction", record.getEtcDeduction());
        map.put("imageUrl", record.getImageUrl());
        map.put("source", "mysql");
        return map;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            // 处理 ISO 格式
            String cleaned = dateTimeStr.replace("T", " ").replace("Z", "");
            if (cleaned.length() == 10) {
                cleaned += " 00:00:00";
            }
            return LocalDateTime.parse(cleaned, DT_FMT);
        } catch (Exception e) {
            log.warn("时间解析失败: {}", dateTimeStr);
            return null;
        }
    }
}
