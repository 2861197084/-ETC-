package com.etc.platform.controller;

import com.etc.platform.dto.ApiResponse;
import com.etc.platform.service.AlertService;
import com.etc.platform.service.HBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private HBaseService hBaseService;

    @Autowired
    private AlertService alertService;

    @GetMapping("/realtime/count")
    public ApiResponse<Long> realtimeCount() {
        long count = hBaseService.getTotalCount();
        return ApiResponse.ok(count);
    }

    @GetMapping("/province/rank")
    public ApiResponse<List<Map<String, Object>>> provinceRank() {
        List<Map<String, Object>> rank = hBaseService.getProvinceRank(10);
        return ApiResponse.ok(rank);
    }

    @GetMapping("/gantry/rank")
    public ApiResponse<List<Map<String, Object>>> gantryRank() {
        List<Map<String, Object>> rank = hBaseService.getGantryRank(10);
        return ApiResponse.ok(rank);
    }

    @GetMapping("/map/heat")
    public ApiResponse<List<Map<String, Object>>> mapHeat() {
        List<Map<String, Object>> heat = hBaseService.getHeatmap();
        return ApiResponse.ok(heat);
    }

    /**
     * 拥堵预测结果列表（当前为模拟数据）
     * GET /api/stats/predict/congestion
     */
    @GetMapping("/predict/congestion")
    public ApiResponse<List<Map<String, Object>>> predictCongestion() {
        List<Map<String, Object>> data = hBaseService.predictCongestion();
        return ApiResponse.ok(data);
    }

    /**
     * 测试接口：触发一条 WebSocket 告警，用于前端联调
     * GET /api/stats/test/alert
     */
    @GetMapping("/test/alert")
    public ApiResponse<String> testAlert() {
        // 简单发送一条套牌车+拥堵告警示例
        alertService.sendFakePlateAlert("粤A12345", "测试告警：疑似套牌车");
        alertService.sendCongestionAlert("G001", "HIGH");
        return ApiResponse.ok("alert sent");
    }

    @GetMapping("/hello")
    public ApiResponse<String> hello() {
        return ApiResponse.ok("后端接口正常，StatsController 已加载，时间：" + LocalDateTime.now());
    }
}