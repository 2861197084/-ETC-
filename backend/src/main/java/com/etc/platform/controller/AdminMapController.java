package com.etc.platform.controller;

import com.etc.platform.dto.ApiResponse;
import com.etc.platform.dto.CheckpointDetail;
import com.etc.platform.dto.HeatmapPoint;
import com.etc.platform.entity.Checkpoint;
import com.etc.platform.service.CheckpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员地图接口 - 从真实数据库读取19个卡口
 */
@RestController
@RequestMapping("/admin/map")
@RequiredArgsConstructor
public class AdminMapController {

    private final CheckpointService checkpointService;

    /**
     * 获取所有卡口（19个出市卡口）
     */
    @GetMapping("/checkpoints")
    public ApiResponse<List<Map<String, Object>>> getCheckpoints(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status) {
        
        List<Checkpoint> checkpoints;
        
        if (type != null && !type.isEmpty()) {
            checkpoints = checkpointService.getCheckpointsByType(type);
        } else if (region != null && !region.isEmpty()) {
            checkpoints = checkpointService.getCheckpointsByRegion(region);
        } else {
            checkpoints = checkpointService.getAllCheckpoints();
        }
        
        // 状态过滤
        if (status != null && !status.isEmpty()) {
            checkpoints = checkpoints.stream()
                    .filter(c -> status.equals(c.getStatusText()))
                    .toList();
        }
        
        // 转换为前端需要的格式
        List<Map<String, Object>> result = checkpoints.stream()
                .map(this::convertToMap)
                .collect(Collectors.toList());
        
        return ApiResponse.ok(result);
    }

    /**
     * 获取单个卡口详情
     */
    @GetMapping("/checkpoints/{id}")
    public ApiResponse<CheckpointDetail> getCheckpoint(@PathVariable Long id) {
        try {
            CheckpointDetail detail = checkpointService.getCheckpointDetail(id);
            return ApiResponse.ok(detail);
        } catch (Exception e) {
            return ApiResponse.error("卡口不存在: " + id, 404);
        }
    }

    /**
     * 获取热力图数据
     */
    @GetMapping("/heatmap")
    public ApiResponse<List<HeatmapPoint>> getHeatmap(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(checkpointService.getHeatmapData());
    }

    /**
     * 获取卡口压力列表
     */
    @GetMapping("/pressure")
    public ApiResponse<List<Map<String, Object>>> getPressure() {
        return ApiResponse.ok(checkpointService.getPressureData());
    }

    /**
     * 将 Checkpoint 转换为 Map (前端兼容格式)
     */
    private Map<String, Object> convertToMap(Checkpoint cp) {
        int maxCapacity = cp.getLaneCount() != null ? cp.getLaneCount() * 800 : 3000;
        
        return Map.ofEntries(
                Map.entry("id", String.valueOf(cp.getId())),
                Map.entry("code", cp.getCode() != null ? cp.getCode() : ""),
                Map.entry("name", cp.getName()),
                Map.entry("fullName", cp.getName()),
                Map.entry("longitude", cp.getLongitude()),
                Map.entry("latitude", cp.getLatitude()),
                Map.entry("region", cp.getDistrict() != null ? cp.getDistrict() : ""),
                Map.entry("road", cp.getRoadName() != null ? cp.getRoadName() : ""),
                Map.entry("boundary", cp.getBoundary()),
                Map.entry("type", cp.getType() != null ? cp.getType() : "provincial"),
                Map.entry("status", cp.getStatusText() != null ? cp.getStatusText() : "normal"),
                Map.entry("currentFlow", cp.getCurrentFlow() != null ? cp.getCurrentFlow() : 0),
                Map.entry("maxCapacity", maxCapacity)
        );
    }
}
