package com.etc.service;

import com.etc.common.CheckpointCatalog;
import com.etc.entity.Checkpoint;
import com.etc.entity.ClonePlateDetection;
import com.etc.entity.Violation;
import com.etc.repository.CheckpointRepository;
import com.etc.repository.ClonePlateDetectionRepository;
import com.etc.repository.PassRecordRepository;
import com.etc.repository.ViolationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RealtimeService {

    private final PassRecordRepository passRecordRepository;
    private final ClonePlateDetectionRepository clonePlateRepository;
    private final ViolationRepository violationRepository;
    private final CheckpointRepository checkpointRepository;
    private final TimeService timeService;
    private final StatsReadService statsReadService;

    /**
     * 获取今日统计数据
     */
    public Map<String, Object> getDailyStats() {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime dayStart = simNow.toLocalDate().atStartOfDay();

        long totalFlow = statsReadService.getMysqlToday();
        if (totalFlow == 0L) {
            Map<String, Long> flowPerHour = getCheckpointFlowPerHour();
            long sumPerHour = flowPerHour.values().stream().mapToLong(Long::longValue).sum();
            long elapsedMinutes = Math.max(0, Duration.between(dayStart, simNow).toMinutes());
            totalFlow = Math.round(sumPerHour * (elapsedMinutes / 60.0));
        }
        stats.put("totalFlow", totalFlow);

        Long violationCount = violationRepository.countByCreateTimeRange(dayStart, simNow);
        Long cloneCount = clonePlateRepository.countByCreateTimeRange(dayStart, simNow);
        long alertCount =
                (violationCount != null ? violationCount : 0L) + (cloneCount != null ? cloneCount : 0L);
        stats.put("alertCount", alertCount);

        // 固定值（后续从 Redis 获取）
        stats.put("totalRevenue", 125680.50);
        stats.put("avgSpeed", 78.5);
        stats.put("checkpointCount", 19);
        stats.put("onlineCount", 19);

        return stats;
    }

    /**
     * 获取套牌车检测列表
     */
    public Page<ClonePlateDetection> getClonePlates(String status, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createTime"));
        String normalizedStatus = normalizeClonePlateStatus(status);
        if (normalizedStatus != null && !normalizedStatus.isEmpty()) {
            return clonePlateRepository.findByStatus(normalizedStatus, pageable);
        }
        return clonePlateRepository.findAll(pageable);
    }

    /**
     * 获取违规记录列表
     */
    public Page<Violation> getViolations(String type, String status, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createTime"));
        if (type != null && !type.isEmpty()) {
            return violationRepository.findByViolationType(type, pageable);
        }
        if (status != null && !status.isEmpty()) {
            return violationRepository.findByStatus(status, pageable);
        }
        return violationRepository.findAll(pageable);
    }

    public boolean updateClonePlateStatus(Long id, String status) {
        if (status == null || status.isBlank()) return false;
        return clonePlateRepository
                .findById(id)
                .map(entity -> {
                    entity.setStatus(normalizeClonePlateStatus(status));
                    clonePlateRepository.save(entity);
                    return true;
                })
                .orElse(false);
    }

    public boolean updateViolationStatus(Long id, String status) {
        if (status == null || status.isBlank()) return false;
        return violationRepository
                .findById(id)
                .map(entity -> {
                    entity.setStatus(String.valueOf(status));
                    violationRepository.save(entity);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 当前 5 分钟窗口内统计出的“每小时流量”（辆/小时）。
     * 若窗口无数据，则使用与模拟时间相关的确定性伪数据，保证演示效果稳定。
     */
    public Map<String, Long> getCheckpointFlowPerHour() {
        LocalDateTime[] window = timeService.getCurrentWindow();
        LocalDateTime start = window[0];
        LocalDateTime end = window[1];

        Map<String, Long> counts =
                passRecordRepository.countByCheckpointInRange(start, end).stream()
                        .filter(Objects::nonNull)
                        .collect(
                                Collectors.toMap(
                                        row -> String.valueOf(row[0]),
                                        row -> ((Number) row[1]).longValue(),
                                        Long::sum));

        boolean hasAny = counts.values().stream().anyMatch(v -> v != null && v > 0);
        if (hasAny) {
            return counts.entrySet().stream()
                    .collect(
                            Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> (e.getValue() != null ? e.getValue() : 0L) * 12L));
        }

        return buildPseudoFlowPerHour(start);
    }

    public List<Map<String, Object>> getPressureWarnings() {
        Map<String, Long> flowPerHour = getCheckpointFlowPerHour();
        List<Checkpoint> checkpoints = checkpointRepository.findAll();

        return checkpoints.stream()
                .map(
                        cp -> {
                            long currentFlow = flowPerHour.getOrDefault(cp.getCode(), 0L);
                            int maxCapacity = cp.getLaneCount() != null ? cp.getLaneCount() * 800 : 3200;
                            double pressureRatio =
                                    maxCapacity <= 0 ? 0 : (double) currentFlow / (double) maxCapacity;
                            int pressure =
                                    (int) Math.max(0, Math.min(100, Math.round(pressureRatio * 100)));

                            String level =
                                    pressureRatio >= 0.8 ? "danger" : (pressureRatio >= 0.55 ? "warning" : "normal");
                            long predictedFlow =
                                    Math.round(
                                            currentFlow
                                                    * (level.equals("danger")
                                                            ? 1.15
                                                            : level.equals("warning") ? 1.08 : 1.03));

                            String suggestion =
                                    switch (level) {
                                        case "danger" -> "建议联动交警分流，增派收费/引导人员，必要时临时限流";
                                        case "warning" -> "建议提前发布拥堵提示，适当分流并关注车道通行效率";
                                        default -> "通行正常，保持巡检";
                                    };

	                            return Map.<String, Object>of(
	                                    "id", cp.getId(),
	                                    "stationName", CheckpointCatalog.displayName(cp.getCode(), cp.getName()),
	                                    "level", level,
	                                    "pressure", pressure,
	                                    "currentFlow", currentFlow,
	                                    "predictedFlow", predictedFlow,
	                                    "suggestion", suggestion);
                        })
                .toList();
    }

    /**
     * 地图页压力列表（前端 /admin/map/pressure）。
     * 字段对齐 frontend/src/types/traffic/checkpoint.ts#CheckpointPressure
     */
    public List<Map<String, Object>> getCheckpointPressureList() {
        Map<String, Long> flowPerHour = getCheckpointFlowPerHour();
        LocalDateTime simNow = timeService.getSimulatedTime();

        return checkpointRepository.findAll().stream()
                .map(cp -> {
                    long currentFlow = flowPerHour.getOrDefault(cp.getCode(), 0L);
                    int maxCapacity = cp.getLaneCount() != null ? cp.getLaneCount() * 800 : 3200;
                    double ratio = maxCapacity <= 0 ? 0 : (double) currentFlow / (double) maxCapacity;

                    String pressureLevel =
                            ratio >= 0.85 ? "high" : (ratio >= 0.65 ? "medium" : (ratio >= 0.4 ? "low" : "smooth"));

                    int predictedDuration =
                            ratio >= 0.85 ? 45 : (ratio >= 0.65 ? 25 : (ratio >= 0.4 ? 10 : 0));

                    String suggestion =
                            switch (pressureLevel) {
                                case "high" -> "建议立即分流并增派引导人员";
                                case "medium" -> "建议提前发布拥堵提示并适当分流";
                                case "low" -> "建议关注车道通行效率，必要时微调放行策略";
                                default -> "通行顺畅";
                            };

                    return Map.<String, Object>of(
                            "checkpointId", cp.getCode(),
                            "checkpointName", CheckpointCatalog.displayName(cp.getCode(), cp.getName()),
                            "currentFlow", currentFlow,
                            "maxCapacity", maxCapacity,
                            "pressureLevel", pressureLevel,
                            "predictedDuration", predictedDuration,
                            "suggestion", suggestion,
                            "updateTime", simNow
                    );
                })
                .toList();
    }

    /**
     * 地图页实时事件（前端 /admin/map/events）。
     * type 可选：violation | clone | all
     */
    public List<Map<String, Object>> getRealtimeEvents(String type, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String t = type != null ? type.trim().toLowerCase(Locale.ROOT) : "all";

        Map<String, Checkpoint> checkpointByCode = checkpointRepository.findAll().stream()
                .collect(Collectors.toMap(Checkpoint::getCode, c -> c, (a, b) -> a));

        List<Map<String, Object>> events = new java.util.ArrayList<>();

        if (!"clone".equals(t)) {
            List<Violation> violations = violationRepository
                    .findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createTime")))
                    .getContent();
	            for (Violation v : violations) {
	                Checkpoint cp = v.getCheckpointId() != null ? checkpointByCode.get(v.getCheckpointId()) : null;
	                Map<String, Object> event = new java.util.HashMap<>();
                event.put("id", "violation-" + v.getId());
                event.put("type", "violation");
                event.put("title", buildViolationDescription(v.getViolationType()));
                event.put("message", v.getPlateNumber() + " " + buildViolationDescription(v.getViolationType()));
	                event.put("plateNumber", v.getPlateNumber());
	                event.put("checkpointId", v.getCheckpointId());
	                event.put(
	                        "checkpointName",
	                        cp != null ? CheckpointCatalog.displayName(cp.getCode(), cp.getName()) : v.getCheckpointName());
	                event.put("longitude", cp != null && cp.getLongitude() != null ? cp.getLongitude().doubleValue() : null);
	                event.put("latitude", cp != null && cp.getLatitude() != null ? cp.getLatitude().doubleValue() : null);
	                event.put("time", v.getViolationTime() != null ? v.getViolationTime() : v.getCreateTime());
	                event.put("status", v.getStatus());
	                events.add(event);
	            }
	        }

        if (!"violation".equals(t)) {
            List<ClonePlateDetection> clones = clonePlateRepository
                    .findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createTime")))
                    .getContent();
	            for (ClonePlateDetection c : clones) {
	                String checkpointId = c.getCheckpointId2() != null ? c.getCheckpointId2() : c.getCheckpointId1();
	                Checkpoint cp = checkpointId != null ? checkpointByCode.get(checkpointId) : null;
	                Map<String, Object> event = new java.util.HashMap<>();
                event.put("id", "clone-" + c.getId());
                event.put("type", "clone");
                event.put("title", "疑似套牌");
                event.put("message", c.getPlateNumber() + " 疑似套牌");
	                event.put("plateNumber", c.getPlateNumber());
	                event.put("checkpointId", checkpointId);
	                event.put("checkpointName", cp != null ? CheckpointCatalog.displayName(cp.getCode(), cp.getName()) : checkpointId);
	                event.put("longitude", cp != null && cp.getLongitude() != null ? cp.getLongitude().doubleValue() : null);
	                event.put("latitude", cp != null && cp.getLatitude() != null ? cp.getLatitude().doubleValue() : null);
	                event.put("time", c.getCreateTime());
	                event.put("status", c.getStatus());
	                event.put("confidence", normalizeConfidence(c.getConfidenceScore()));
	                events.add(event);
	            }
	        }

        // 取最新的 safeLimit 条
        return events.stream()
                .sorted((a, b) -> {
                    Object ta = a.get("time");
                    Object tb = b.get("time");
                    if (ta instanceof LocalDateTime && tb instanceof LocalDateTime) {
                        return ((LocalDateTime) tb).compareTo((LocalDateTime) ta);
                    }
                    return 0;
                })
                .limit(safeLimit)
                .toList();
    }

    public List<Map<String, Object>> toClonePlateDtos(List<ClonePlateDetection> entities) {
        Map<String, String> checkpointNameByCode =
                checkpointRepository.findAll().stream()
                        .collect(Collectors.toMap(
                                Checkpoint::getCode,
                                cp -> CheckpointCatalog.displayName(cp.getCode(), cp.getName()),
                                (a, b) -> a));

        return entities.stream()
                .map(
                        e -> {
                            String cp1 = e.getCheckpointId1();
                            String cp2 = e.getCheckpointId2();
                            String cp1Name = cp1 != null ? checkpointNameByCode.getOrDefault(cp1, cp1) : null;
                            String cp2Name = cp2 != null ? checkpointNameByCode.getOrDefault(cp2, cp2) : null;

                            LocalDateTime detectTime = e.getCreateTime();
                            double confidence = normalizeConfidence(e.getConfidenceScore());

                            Map<String, Object> map = new java.util.HashMap<>();
                            map.put("id", e.getId());
                            map.put("plateNumber", e.getPlateNumber());
                            map.put("checkpointId1", e.getCheckpointId1());
                            map.put("checkpointId2", e.getCheckpointId2());
                            map.put("checkpoint1Id", e.getCheckpointId1());
                            map.put("checkpoint2Id", e.getCheckpointId2());
                            map.put("checkpoint1", cp1Name);
                            map.put("checkpoint2", cp2Name);
                            map.put("checkpoint1Name", cp1Name);
                            map.put("checkpoint2Name", cp2Name);
                            map.put("time1", e.getTime1());
                            map.put("time2", e.getTime2());
                            map.put("checkpoint1Time", e.getTime1());
                            map.put("checkpoint2Time", e.getTime2());
                            map.put("distanceKm", e.getDistanceKm());
                            map.put("timeDiffMinutes", e.getTimeDiffMinutes());
                            map.put("minSpeedRequired", e.getMinSpeedRequired());
                            map.put("confidence", confidence);
                            map.put("status", e.getStatus());
                            map.put("detectTime", detectTime);
                            map.put("detectionTime", detectTime);
                            map.put("distance", e.getDistanceKm());
                            map.put("timeDiff", e.getTimeDiffMinutes());
                            map.put("calculatedSpeed", e.getMinSpeedRequired());
                            return map;
                        })
                .toList();
    }

    public List<Map<String, Object>> toViolationDtos(List<Violation> entities) {
        return entities.stream()
                .map(
                        v -> {
                            Map<String, Object> map = new java.util.HashMap<>();
                            map.put("id", v.getId());
                            map.put("plateNumber", v.getPlateNumber());
                            map.put("type", v.getViolationType());
                            map.put("violationType", v.getViolationType());
                            map.put("description", buildViolationDescription(v.getViolationType()));
                            map.put("checkpointId", v.getCheckpointId());
                            map.put("checkpointName", v.getCheckpointName());
                            map.put("checkpoint", v.getCheckpointName());
                            map.put("detectTime", v.getViolationTime());
                            map.put("time", v.getViolationTime());
                            map.put("status", v.getStatus());
                            return map;
                        })
                .toList();
    }

    private String buildViolationDescription(String type) {
        if (type == null) return "违规";
        String t = type.toLowerCase(Locale.ROOT);
        if (t.contains("over") || t.contains("speed")) return "超速";
        if (t.contains("load")) return "超载";
        if (t.contains("illegal") || t.contains("forbidden")) return "违规";
        return type;
    }

    private Map<String, Long> buildPseudoFlowPerHour(LocalDateTime simTime) {
        List<Checkpoint> checkpoints = checkpointRepository.findAll();
        long minuteOfDay = simTime.getHour() * 60L + simTime.getMinute();
        return checkpoints.stream()
                .collect(
                        Collectors.toMap(
                                Checkpoint::getCode,
                                cp -> {
                                    int seed = Math.abs(cp.getCode().hashCode());
                                    long base = 200 + (seed % 900);
                                    long wave = (minuteOfDay * 7 + seed) % 220;
                                    return base + wave;
                                }));
    }

    private String normalizeClonePlateStatus(String status) {
        if (status == null) return null;
        String s = status.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "0", "pending" -> "pending";
            case "1", "confirmed" -> "confirmed";
            case "2", "dismissed" -> "dismissed";
            default -> status;
        };
    }

    private double normalizeConfidence(BigDecimal score) {
        if (score == null) return 0.0;
        double v = score.doubleValue();
        if (v > 1.0) return Math.max(0.0, Math.min(1.0, v / 100.0));
        return Math.max(0.0, Math.min(1.0, v));
    }
}
