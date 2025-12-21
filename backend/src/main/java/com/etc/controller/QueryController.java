package com.etc.controller;

import com.etc.common.ApiResponse;
import com.etc.entity.PassRecord;
import com.etc.service.QueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/query")
@RequiredArgsConstructor
@Tag(name = "查询接口", description = "数据查询相关接口")
public class QueryController {

    private final QueryService queryService;

    @GetMapping("/records")
    @Operation(summary = "查询通行记录")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchRecords(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String checkpointId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<PassRecord> result =
                queryService.search(plateNumber, checkpointId, startTime, endTime, page, pageSize);

        // 转换为前端需要的格式
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
                    return map;
                })
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("list", list);
        response.put("total", result.getTotalElements());
        response.put("page", page);
        response.put("pageSize", pageSize);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/records/{id}")
    @Operation(summary = "获取记录详情")
    public ResponseEntity<ApiResponse<PassRecord>> getRecordDetail(@PathVariable Long id) {
        return queryService.findById(id)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "record not found")));
    }

    @PostMapping("/text2sql")
    @Operation(summary = "自然语言转SQL")
    public ResponseEntity<ApiResponse<Map<String, Object>>> text2sql(@RequestBody Map<String, String> request) {
        String query = request.get("query");

        // 简单的关键词匹配实现
        String sql;
        String explanation;

        if (query.contains("今天") || query.contains("今日")) {
            sql = "SELECT * FROM pass_record WHERE DATE(gcsj) = CURRENT_DATE ORDER BY gcsj DESC LIMIT 100";
            explanation = "查询今日的通行记录";
        } else if (query.contains("套牌")) {
            sql = "SELECT * FROM clone_plate_detection ORDER BY create_time DESC LIMIT 100";
            explanation = "查询套牌车检测记录";
        } else if (query.contains("统计") || query.contains("数量")) {
            sql = "SELECT COUNT(*) as total FROM pass_record";
            explanation = "统计通行记录总数";
        } else {
            sql = "SELECT * FROM pass_record ORDER BY gcsj DESC LIMIT 100";
            explanation = "查询最近的通行记录";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("sql", sql);
        response.put("explanation", explanation);
        response.put("confidence", 0.85);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/execute")
    @Operation(summary = "执行SQL查询")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeQuery(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");

        // 安全检查：只允许 SELECT 语句
        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            return ResponseEntity.ok(ApiResponse.error(400, "只允许执行SELECT语句"));
        }

        // 暂时返回模拟数据，后续接入 Trino 执行
        Map<String, Object> response = new HashMap<>();
        response.put("columns", List.of("id", "plateNumber", "checkpointName", "passTime"));
        response.put("data", List.of());
        response.put("total", 0);
        response.put("message", "SQL执行功能开发中，请使用标准查询接口");
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/options/checkpoints")
    @Operation(summary = "获取卡口选项列表")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getCheckpointOptions() {
        // 返回 19 个卡口选项
        List<Map<String, String>> options = List.of(
                Map.of("label", "苏皖界1(104省道)", "value", "CP001"),
                Map.of("label", "苏皖界2(311国道)", "value", "CP002"),
                Map.of("label", "苏皖界3(徐明高速)", "value", "CP003"),
                Map.of("label", "苏皖界4(宿新高速)", "value", "CP004"),
                Map.of("label", "苏皖界5(徐淮高速)", "value", "CP005"),
                Map.of("label", "苏皖界6(新扬高速)", "value", "CP006"),
                Map.of("label", "苏鲁界1(206国道)", "value", "CP007"),
                Map.of("label", "苏鲁界2(104国道)", "value", "CP008"),
                Map.of("label", "苏鲁界3(京台高速)", "value", "CP009"),
                Map.of("label", "苏鲁界4(枣庄连接线)", "value", "CP010"));
        return ResponseEntity.ok(ApiResponse.success(options));
    }

    @GetMapping("/options/vehicle-types")
    @Operation(summary = "获取车辆类型选项")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getVehicleTypeOptions() {
        List<Map<String, String>> options = List.of(
                Map.of("label", "小型客车", "value", "小型客车"),
                Map.of("label", "中型客车", "value", "中型客车"),
                Map.of("label", "小型货车", "value", "小型货车"),
                Map.of("label", "大型货车", "value", "大型货车"));
        return ResponseEntity.ok(ApiResponse.success(options));
    }
}
