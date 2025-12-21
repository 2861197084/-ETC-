package com.etc.controller;

import com.etc.common.ApiResponse;
import com.etc.entity.Checkpoint;
import com.etc.service.CheckpointService;
import com.etc.service.RealtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/map")
@RequiredArgsConstructor
@Tag(name = "地图接口", description = "卡口地图相关接口")
public class MapController {

    private final CheckpointService checkpointService;
    private final RealtimeService realtimeService;

    @GetMapping("/checkpoints")
    @Operation(summary = "获取卡口列表")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCheckpoints(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status) {
        List<Checkpoint> checkpoints;
        if (region != null && !region.isEmpty()) {
            checkpoints = checkpointService.findByDistrict(region);
        } else if (status != null && !status.isEmpty()) {
            Integer statusValue = null;
            if ("online".equalsIgnoreCase(status)) statusValue = 1;
            if ("offline".equalsIgnoreCase(status)) statusValue = 0;
            if (statusValue == null) statusValue = Integer.parseInt(status);
            checkpoints = checkpointService.findByStatus(statusValue);
        } else {
            checkpoints = checkpointService.findAll();
        }

        Map<String, Long> checkpointFlowPerHour = realtimeService.getCheckpointFlowPerHour();

        // 转换为前端需要的格式
        List<Map<String, Object>> result = checkpoints.stream()
                .map(cp -> {
                    String type = com.etc.common.CheckpointCatalog.displayType(cp.getCode(), "municipal");

                    String displayName = com.etc.common.CheckpointCatalog.displayName(cp.getCode(), cp.getName());
                    String displayDistrict = com.etc.common.CheckpointCatalog.displayDistrict(cp.getCode(), cp.getDistrict());

                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", cp.getId());
                    map.put("code", cp.getCode());
                    map.put("name", displayName);
                    map.put("fullName", displayName);
                    map.put("district", displayDistrict != null ? displayDistrict : "");
                    map.put("region", displayDistrict != null ? displayDistrict : "");
                    map.put("longitude", cp.getLongitude() != null ? cp.getLongitude().doubleValue() : 0);
                    map.put("latitude", cp.getLatitude() != null ? cp.getLatitude().doubleValue() : 0);
                    map.put("type", type);
                    map.put("road", cp.getRoadName() != null ? cp.getRoadName() : "");
                    map.put("boundary", "");
                    map.put("status", cp.getStatus() != null && cp.getStatus() == 1 ? "online" : "offline");
                    map.put("currentFlow", checkpointFlowPerHour.getOrDefault(cp.getCode(), 0L));
                    map.put("maxCapacity", cp.getLaneCount() != null ? cp.getLaneCount() * 800 : 3200);
                    map.put("direction", cp.getDirection() != null ? cp.getDirection() : "双向");
                    map.put("roadName", cp.getRoadName() != null ? cp.getRoadName() : "");
                    map.put("laneCount", cp.getLaneCount() != null ? cp.getLaneCount() : 4);
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/checkpoints/{id}")
    @Operation(summary = "获取卡口详情")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCheckpointDetail(@PathVariable String id) {
        return checkpointService.findByCode(id)
                .or(() -> {
                    try {
                        return checkpointService.findById(Long.parseLong(id));
                    } catch (NumberFormatException e) {
                        return java.util.Optional.empty();
                    }
                })
                .map(cp -> {
                    String displayName = com.etc.common.CheckpointCatalog.displayName(cp.getCode(), cp.getName());
                    String displayDistrict = com.etc.common.CheckpointCatalog.displayDistrict(cp.getCode(), cp.getDistrict());
                    String type = com.etc.common.CheckpointCatalog.displayType(cp.getCode(), "municipal");

                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", cp.getId());
                    map.put("code", cp.getCode());
                    map.put("name", displayName);
                    map.put("fullName", displayName);
                    map.put("district", displayDistrict != null ? displayDistrict : "");
                    map.put("region", displayDistrict != null ? displayDistrict : "");
                    map.put("longitude", cp.getLongitude() != null ? cp.getLongitude().doubleValue() : 0);
                    map.put("latitude", cp.getLatitude() != null ? cp.getLatitude().doubleValue() : 0);
                    map.put("type", type);
                    map.put("status", cp.getStatus() != null && cp.getStatus() == 1 ? "online" : "offline");
                    map.put("road", cp.getRoadName() != null ? cp.getRoadName() : "");
                    map.put("boundary", "");
                    map.put("roadName", cp.getRoadName() != null ? cp.getRoadName() : "");
                    map.put("laneCount", cp.getLaneCount() != null ? cp.getLaneCount() : 4);
                    return ResponseEntity.ok(ApiResponse.success(map));
                })
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "checkpoint not found")));
    }

    @GetMapping("/heatmap")
    @Operation(summary = "获取交通热力图数据")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHeatmap() {
        // 返回卡口热力数据
        List<Checkpoint> checkpoints = checkpointService.findAll();
        Map<String, Long> checkpointFlowPerHour = realtimeService.getCheckpointFlowPerHour();
        List<Map<String, Object>> heatmapData = checkpoints.stream()
                .map(cp -> Map.<String, Object>of(
                        "lng", cp.getLongitude() != null ? cp.getLongitude().doubleValue() : 0,
                        "lat", cp.getLatitude() != null ? cp.getLatitude().doubleValue() : 0,
                        "count", checkpointFlowPerHour.getOrDefault(cp.getCode(), 0L)
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(heatmapData));
    }

    @GetMapping("/pressure")
    @Operation(summary = "获取站口压力列表")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPressureList() {
        return ResponseEntity.ok(ApiResponse.success(realtimeService.getCheckpointPressureList()));
    }

    @GetMapping("/events")
    @Operation(summary = "获取实时事件列表")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRealtimeEvents(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(realtimeService.getRealtimeEvents(type, limit)));
    }
}
