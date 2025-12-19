package com.etc.platform.controller;

import com.etc.platform.dto.ApiResponse;
import com.etc.platform.dto.PageResult;
import com.etc.platform.service.PassRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员数据查询接口
 */
@RestController
@RequestMapping("/admin/query")
public class AdminQueryController {

    private final PassRecordService passRecordService;

    public AdminQueryController(PassRecordService passRecordService) {
        this.passRecordService = passRecordService;
    }

    /**
     * 通行记录查询
     */
    @GetMapping("/records")
    public ApiResponse<PageResult<Map<String, Object>>> queryRecords(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String checkpointId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) String queryType,
            @RequestParam(required = false) Integer minSpeed,
            @RequestParam(required = false) String violationType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 解析时间参数
        java.time.LocalDateTime start = null;
        java.time.LocalDateTime end = null;
        try {
            if (startTime != null && !startTime.isEmpty()) {
                start = java.time.LocalDateTime.parse(startTime.replace("Z", ""));
            }
            if (endTime != null && !endTime.isEmpty()) {
                end = java.time.LocalDateTime.parse(endTime.replace("Z", ""));
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        
        PageResult<Map<String, Object>> result = passRecordService.queryRecords(
                plateNumber, checkpointId, start, end, vehicleType, 
                queryType, minSpeed, violationType, page, pageSize);
        return ApiResponse.ok(result);
    }

    /**
     * 记录详情
     */
    @GetMapping("/records/{id}")
    public ApiResponse<Map<String, Object>> getRecord(@PathVariable Long id) {
        Map<String, Object> record = passRecordService.getRecordById(id);
        if (record == null) {
            return ApiResponse.error("记录不存在", 404);
        }
        return ApiResponse.ok(record);
    }

    /**
     * 自然语言转SQL
     */
    @PostMapping("/text2sql")
    public ApiResponse<Map<String, Object>> text2Sql(@RequestBody Map<String, String> params) {
        String query = params.get("query");
        return ApiResponse.ok(passRecordService.text2Sql(query));
    }

    /**
     * 执行SQL
     */
    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> executeSql(@RequestBody Map<String, String> params) {
        String sql = params.get("sql");
        return ApiResponse.ok(passRecordService.executeSql(sql));
    }

    /**
     * 查询历史
     */
    @GetMapping("/history")
    public ApiResponse<PageResult<Map<String, Object>>> getHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 返回空列表
        return ApiResponse.ok(PageResult.of(List.of(), 0L, page, pageSize));
    }

    /**
     * 导出结果
     */
    @PostMapping("/export")
    public ApiResponse<String> exportResults(@RequestBody Map<String, String> params) {
        // 返回模拟下载链接
        return ApiResponse.ok("/api/download/export-" + System.currentTimeMillis() + ".xlsx");
    }

    /**
     * 卡口选项
     */
    @GetMapping("/options/checkpoints")
    public ApiResponse<List<Map<String, Object>>> getCheckpointOptions() {
        return ApiResponse.ok(passRecordService.getCheckpointOptions());
    }

    /**
     * 车辆类型选项
     */
    @GetMapping("/options/vehicle-types")
    public ApiResponse<List<Map<String, String>>> getVehicleTypeOptions() {
        return ApiResponse.ok(passRecordService.getVehicleTypeOptions());
    }
}
