package com.etc.controller;

import com.etc.common.ApiResponse;
import com.etc.service.ForecastService;
import com.etc.service.TimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/analysis")
@RequiredArgsConstructor
@Tag(name = "预测分析接口", description = "Time-MoE 预测分析（未来12×5min）")
public class AnalysisController {

    private static final DateTimeFormatter MYSQL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ForecastService forecastService;
    private final TimeService timeService;

    private static final String DEFAULT_MODEL_VERSION = "timemoe_etc_flow_v1";
    private static final int DEFAULT_FREQ_MIN = 5;
    private static final int DEFAULT_PRED_LEN = 12;

    @PostMapping("/forecast/refresh")
    @Operation(summary = "触发新一轮预测（写入请求队列）", description = "前端每分钟调用一次，Spark 作业会处理 pending 请求并落库预测结果。")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(@RequestBody Map<String, Object> body) {
        String checkpointId = body.get("checkpointId") != null ? String.valueOf(body.get("checkpointId")) : "";
        String fxlx = body.get("fxlx") != null ? String.valueOf(body.get("fxlx")) : "";
        String asOfTimeStr = body.get("asOfTime") != null ? String.valueOf(body.get("asOfTime")) : "";
        String modelVersion = body.get("modelVersion") != null ? String.valueOf(body.get("modelVersion")) : DEFAULT_MODEL_VERSION;

        if (checkpointId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error(400, "checkpointId is required"));
        }
        if (!("1".equals(fxlx) || "2".equals(fxlx))) {
            return ResponseEntity.ok(ApiResponse.error(400, "fxlx must be '1' or '2'"));
        }

        LocalDateTime asOfTime;
        if (asOfTimeStr != null && !asOfTimeStr.isBlank()) {
            try {
                asOfTime = LocalDateTime.parse(asOfTimeStr, MYSQL_TS);
            } catch (Exception e) {
                return ResponseEntity.ok(ApiResponse.error(400, "invalid asOfTime, expected yyyy-MM-dd HH:mm:ss"));
            }
        } else {
            asOfTime = timeService.getSimulatedTime();
        }

        long requestId = forecastService.upsertPendingForecastRequest(checkpointId, fxlx, asOfTime, modelVersion);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "requestId", requestId,
                "checkpointId", checkpointId,
                "fxlx", fxlx,
                "asOfTime", asOfTime.format(MYSQL_TS),
                "modelVersion", modelVersion
        )));
    }

    @GetMapping("/forecast/latest")
    @Operation(summary = "获取最新预测结果（未来12点）", description = "若有未完成请求则返回 pending=true。")
    public ResponseEntity<ApiResponse<Map<String, Object>>> latest(
            @RequestParam String checkpointId,
            @RequestParam String fxlx,
            @RequestParam(required = false) String modelVersion
    ) {
        String mv = (modelVersion == null || modelVersion.isBlank()) ? DEFAULT_MODEL_VERSION : modelVersion;

        if (checkpointId == null || checkpointId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error(400, "checkpointId is required"));
        }
        if (!("1".equals(fxlx) || "2".equals(fxlx))) {
            return ResponseEntity.ok(ApiResponse.error(400, "fxlx must be '1' or '2'"));
        }

        Optional<ForecastService.ForecastRow> latest = forecastService.findLatestForecast(checkpointId, fxlx, mv);
        Optional<ForecastService.PendingRequest> pending = forecastService.findLatestPendingRequest(checkpointId, fxlx, mv);

        boolean isPending = pending.isPresent();
        Long pendingRequestId = pending.map(ForecastService.PendingRequest::id).orElse(null);
        String pendingCreatedAt = pending.map(ForecastService.PendingRequest::createdAt).orElse(null);

        // 注意：Map.of 不允许 null value，会触发 NPE；这里用可变 Map 更稳健
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkpointId", checkpointId);
        payload.put("fxlx", fxlx);
        payload.put("modelVersion", mv);
        payload.put("pending", isPending);
        if (pendingRequestId != null) {
            payload.put("requestId", pendingRequestId);
        }
        if (pendingCreatedAt != null && !pendingCreatedAt.isBlank() && !"null".equalsIgnoreCase(pendingCreatedAt)) {
            payload.put("requestCreatedAt", pendingCreatedAt);
        }

        if (latest.isEmpty()) {
            payload.put("startTime", "");
            payload.put("updatedAt", "");
            payload.put("times", List.of());
            payload.put("values", List.of());
            return ResponseEntity.ok(ApiResponse.success(payload));
        }

        ForecastService.ForecastRow r = latest.get();
        payload.put("checkpointId", r.checkpointId());
        payload.put("fxlx", r.fxlx());
        payload.put("modelVersion", r.modelVersion());
        payload.put("updatedAt", r.updatedAt());
        payload.put("startTime", r.startTime());
        payload.put("times", buildTimes(r.startTime(), DEFAULT_PRED_LEN, DEFAULT_FREQ_MIN));
        payload.put("values", r.values() == null ? List.of() : r.values());

        return ResponseEntity.ok(ApiResponse.success(payload));
    }

    private static List<String> buildTimes(String startTime, int predLen, int freqMin) {
        if (startTime == null || startTime.isBlank()) return List.of();
        LocalDateTime base;
        try {
            base = LocalDateTime.parse(startTime, MYSQL_TS);
        } catch (Exception e) {
            return List.of();
        }
        List<String> out = new ArrayList<>(predLen);
        for (int i = 0; i < predLen; i++) {
            out.add(base.plusMinutes((long) i * freqMin).format(MYSQL_TS));
        }
        return out;
    }
}


