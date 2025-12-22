package com.etc.controller;

import com.etc.common.ApiResponse;
import com.etc.entity.ClonePlateDetection;
import com.etc.entity.Violation;
import com.etc.service.RealtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/realtime")
@RequiredArgsConstructor
@Tag(name = "实时数据接口", description = "实时监控相关接口")
public class RealtimeController {

    private final RealtimeService realtimeService;

    @GetMapping("/daily-stats")
    @Operation(summary = "获取今日统计数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDailyStats() {
        return ResponseEntity.ok(ApiResponse.success(realtimeService.getDailyStats()));
    }

    @GetMapping("/vehicle-source")
    @Operation(summary = "获取车辆来源统计（本地/外地）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVehicleSource() {
        return ResponseEntity.ok(ApiResponse.success(realtimeService.getVehicleSourceStats()));
    }

    @GetMapping("/clone-plates")
    @Operation(summary = "获取套牌车检测列表")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getClonePlates(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<ClonePlateDetection> result = realtimeService.getClonePlates(status, plateNumber, startTime, endTime, page, pageSize);
        Map<String, Object> response = new HashMap<>();
        response.put("list", realtimeService.toClonePlateDtos(result.getContent()));
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/clone-plates/{id}")
    @Operation(summary = "处理套牌车检测")
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleClonePlate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String status = body.get("status") != null ? String.valueOf(body.get("status")) : null;
        boolean updated = realtimeService.updateClonePlateStatus(id, status);
        if (!updated) {
            return ResponseEntity.ok(ApiResponse.error(404, "clone plate record not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
    }

    @GetMapping("/violations")
    @Operation(summary = "获取违规记录列表")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getViolations(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<Violation> result = realtimeService.getViolations(type, status, page, pageSize);
        Map<String, Object> response = new HashMap<>();
        response.put("list", realtimeService.toViolationDtos(result.getContent()));
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/violations/{id}")
    @Operation(summary = "处理违规记录")
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleViolation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String status = body.get("status") != null ? String.valueOf(body.get("status")) : null;
        boolean updated = realtimeService.updateViolationStatus(id, status);
        if (!updated) {
            return ResponseEntity.ok(ApiResponse.error(404, "violation record not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
    }

    @GetMapping("/pressure-warnings")
    @Operation(summary = "获取站口压力预警")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPressureWarnings() {
        return ResponseEntity.ok(ApiResponse.success(realtimeService.getPressureWarnings()));
    }

    @GetMapping("/flow")
    @Operation(summary = "获取实时流量数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRealtimeFlow() {
        Map<String, Object> flow = new HashMap<>();
        flow.put("timestamp", System.currentTimeMillis());
        flow.put("totalFlow", realtimeService.getDailyStats().get("totalFlow"));
        return ResponseEntity.ok(ApiResponse.success(flow));
    }
}
