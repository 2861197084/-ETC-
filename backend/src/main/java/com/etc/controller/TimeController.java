package com.etc.controller;

import com.etc.service.TimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/time")
@RequiredArgsConstructor
@Tag(name = "时间模拟接口", description = "模拟时间管理")
public class TimeController {

    private final TimeService timeService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 统一成功响应 */
    private Map<String, Object> success(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "success");
        response.put("data", data);
        return response;
    }

    private Map<String, Object> buildTimeData() {
        Map<String, Object> data = new HashMap<>();
        LocalDateTime simTime = timeService.getSimulatedTime();
        LocalDateTime realTime = timeService.getRealTime();
        LocalDateTime[] window = timeService.getCurrentWindow();

        data.put("simulatedTime", simTime.format(FORMATTER));
        data.put("simulatedTimestamp", java.sql.Timestamp.valueOf(simTime).getTime());
        data.put("realTime", realTime.format(FORMATTER));
        data.put("timeScale", timeService.getTimeScale());
        data.put("isRunning", timeService.isRunning());
        data.put("windowStart", window[0].format(FORMATTER));
        data.put("windowEnd", window[1].format(FORMATTER));
        return data;
    }

    @GetMapping
    @Operation(summary = "获取当前时间状态")
    public ResponseEntity<Map<String, Object>> getTimeStatus() {
        return ResponseEntity.ok(success(buildTimeData()));
    }

    @PostMapping("/start")
    @Operation(summary = "启动时间模拟")
    public ResponseEntity<Map<String, Object>> start() {
        timeService.start();
        return ResponseEntity.ok(success(buildTimeData()));
    }

    @PostMapping("/pause")
    @Operation(summary = "暂停时间模拟")
    public ResponseEntity<Map<String, Object>> pause() {
        timeService.pause();
        return ResponseEntity.ok(success(buildTimeData()));
    }

    @PostMapping("/reset")
    @Operation(summary = "重置时间到 2024-01-01 00:00")
    public ResponseEntity<Map<String, Object>> reset() {
        timeService.reset();
        return ResponseEntity.ok(success(buildTimeData()));
    }
}
