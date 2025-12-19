package com.etc.platform.controller;

import com.etc.platform.dto.ApiResponse;
import com.etc.platform.dto.FlowPrediction;
import com.etc.platform.entity.Checkpoint;
import com.etc.platform.service.CheckpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流量预测接口
 */
@RestController
@RequestMapping("/admin/predict")
@RequiredArgsConstructor
public class AdminPredictController {

    private final CheckpointService checkpointService;

    /**
     * 获取单个卡口的流量预测
     */
    @GetMapping("/checkpoint/{id}")
    public ApiResponse<FlowPrediction> getPrediction(
            @PathVariable Long id,
            @RequestParam(defaultValue = "24") int hours) {
        try {
            FlowPrediction prediction = checkpointService.getFlowPrediction(id);
            return ApiResponse.ok(prediction);
        } catch (Exception e) {
            return ApiResponse.error("获取预测失败: " + e.getMessage(), 500);
        }
    }

    /**
     * 获取所有卡口的预测汇总
     */
    @GetMapping("/checkpoints")
    public ApiResponse<List<Map<String, Object>>> getAllPredictions(
            @RequestParam(defaultValue = "24") int hours) {
        List<Checkpoint> checkpoints = checkpointService.getAllCheckpoints();
        
        List<Map<String, Object>> predictions = checkpoints.stream()
                .map(cp -> {
                    FlowPrediction pred = checkpointService.getFlowPrediction(cp.getId());
                    return Map.<String, Object>of(
                            "checkpointId", cp.getId(),
                            "checkpointName", cp.getName(),
                            "peakHour", pred.getPeakHour(),
                            "peakFlow", pred.getPeakFlow(),
                            "currentStatus", cp.getStatusText()
                    );
                })
                .collect(Collectors.toList());
        
        return ApiResponse.ok(predictions);
    }

    /**
     * 获取卡口历史数据
     */
    @GetMapping("/history/{id}")
    public ApiResponse<Map<String, Object>> getHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "7") int days) {
        try {
            Checkpoint cp = checkpointService.getAllCheckpoints().stream()
                    .filter(c -> c.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("卡口不存在"));
            
            // 构建历史数据
            Map<String, Object> history = Map.of(
                    "checkpointId", cp.getId(),
                    "checkpointName", cp.getName(),
                    "days", days,
                    "message", "历史数据功能开发中"
            );
            
            return ApiResponse.ok(history);
        } catch (Exception e) {
            return ApiResponse.error("获取历史数据失败: " + e.getMessage(), 500);
        }
    }
}
