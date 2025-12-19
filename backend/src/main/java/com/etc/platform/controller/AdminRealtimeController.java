package com.etc.platform.controller;

import com.etc.platform.dto.ApiResponse;
import com.etc.platform.dto.DailyStats;
import com.etc.platform.dto.PageResult;
import com.etc.platform.service.RealtimeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员实时数据接口
 */
@RestController
@RequestMapping("/admin/realtime")
public class AdminRealtimeController {

    private final RealtimeService realtimeService;

    public AdminRealtimeController(RealtimeService realtimeService) {
        this.realtimeService = realtimeService;
    }

    /**
     * 今日统计
     */
    @GetMapping("/daily-stats")
    public ApiResponse<DailyStats> getDailyStats() {
        return ApiResponse.ok(realtimeService.getDailyStats());
    }

    /**
     * 实时流量数据
     */
    @GetMapping("/flow")
    public ApiResponse<List<Map<String, Object>>> getRealtimeFlow() {
        return ApiResponse.ok(realtimeService.getRealtimeFlow());
    }

    /**
     * 套牌车检测列表
     */
    @GetMapping("/clone-plates")
    public ApiResponse<PageResult<Map<String, Object>>> getClonePlates(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        List<Map<String, Object>> list = realtimeService.getClonePlateDetections(status, page, pageSize);
        return ApiResponse.ok(PageResult.of(list, (long) list.size(), page, pageSize));
    }

    /**
     * 处理套牌车
     */
    @PutMapping("/clone-plates/{id}")
    public ApiResponse<Void> handleClonePlate(
            @PathVariable String id,
            @RequestBody Map<String, String> params) {
        // 模拟处理
        return ApiResponse.ok(null);
    }

    /**
     * 违规列表 - 支持类型筛选
     */
    @GetMapping("/violations")
    public ApiResponse<PageResult<Map<String, Object>>> getViolations(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        List<Map<String, Object>> list = realtimeService.getViolationsWithFilter(type, status, page, pageSize);
        long total = realtimeService.countViolations(type, status);
        return ApiResponse.ok(PageResult.of(list, total, page, pageSize));
    }

    /**
     * 处理违规
     */
    @PutMapping("/violations/{id}")
    public ApiResponse<Void> handleViolation(
            @PathVariable String id,
            @RequestBody Map<String, String> params) {
        // 模拟处理
        return ApiResponse.ok(null);
    }

    /**
     * 站口压力预警 - 返回全部19个卡口
     */
    @GetMapping("/pressure-warnings")
    public ApiResponse<List<Map<String, Object>>> getPressureWarnings() {
        return ApiResponse.ok(realtimeService.getAllPressureWarnings());
    }
    
    /**
     * 车辆来源统计（本地/外地）
     */
    @GetMapping("/vehicle-source")
    public ApiResponse<Map<String, Object>> getVehicleSource() {
        return ApiResponse.ok(realtimeService.getVehicleSourceStats());
    }
    
    /**
     * 区域热度排名
     */
    @GetMapping("/region-heat")
    public ApiResponse<List<Map<String, Object>>> getRegionHeat() {
        return ApiResponse.ok(realtimeService.getRegionHeatRanking());
    }
}
