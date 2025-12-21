package com.etc.controller;

import com.etc.common.ApiResponse;
import com.etc.repository.PassRecordRepository;
import com.etc.service.StatsReadService;
import com.etc.service.TimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/progressive/count")
@RequiredArgsConstructor
@Tag(name = "渐进式统计", description = "用于渐进式加载的计数/统计接口（Redis 可缓存）")
public class ProgressiveCountController {

    private final PassRecordRepository passRecordRepository;
    private final TimeService timeService;
    private final StatsReadService statsReadService;

    @GetMapping("/plate/{plateNumber}")
    @Operation(summary = "获取车牌通行统计")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlateCount(@PathVariable String plateNumber) {
        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime startOfDay = simNow.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        long totalCount = passRecordRepository.countByHp(plateNumber);
        long todayCount = passRecordRepository.countByHpAndGcsjBetween(plateNumber, startOfDay, endOfDay);

        Map<String, Object> data = new HashMap<>();
        data.put("plateNumber", plateNumber);
        data.put("totalCount", totalCount);
        data.put("todayCount", todayCount);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/checkpoint/{checkpointId}")
    @Operation(summary = "获取卡口通行统计")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCheckpointCount(@PathVariable String checkpointId) {
        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime startOfDay = simNow.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        long totalCount = passRecordRepository.countByCheckpointId(checkpointId);
        long todayCount = passRecordRepository.countByCheckpointIdAndGcsjBetween(checkpointId, startOfDay, endOfDay);

        Map<String, Object> data = new HashMap<>();
        data.put("checkpointId", checkpointId);
        data.put("totalCount", totalCount);
        data.put("todayCount", todayCount);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/global")
    @Operation(summary = "获取全局通行统计")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGlobalCount() {
        long todayCount = statsReadService.getMysqlToday();
        long totalCount = statsReadService.getCombinedTotal();
        Map<String, Long> checkpointCounts = statsReadService.getMysqlTodayByCheckpoint();

        Map<String, Object> data = new HashMap<>();
        data.put("todayCount", todayCount);
        data.put("totalCount", totalCount);
        data.put("checkpointCounts", checkpointCounts);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
