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

        // Use simulated timeline fields (violation_time / time_2) rather than ingestion time.
        Long violationCount = violationRepository.countByViolationTimeRange(dayStart, simNow);
        Long cloneCount = clonePlateRepository.countByTime2Range(dayStart, simNow);
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
     * 获取车辆来源统计（本地/外地）
     * 本地车辆：苏C开头（徐州）
     * 外地车辆：非苏C开头
     */
    public Map<String, Object> getVehicleSourceStats() {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime dayStart = simNow.toLocalDate().atStartOfDay();

        Long localCount = passRecordRepository.countLocalVehicles(dayStart, simNow);
        Long foreignCount = passRecordRepository.countForeignVehicles(dayStart, simNow);

        long local = localCount != null ? localCount : 0L;
        long foreign = foreignCount != null ? foreignCount : 0L;
        long total = local + foreign;

        stats.put("local", local);
        stats.put("foreign", foreign);
        stats.put("total", total);
        stats.put("localRate", total > 0 ? Math.round(local * 100.0 / total) : 0);
        stats.put("foreignRate", total > 0 ? Math.round(foreign * 100.0 / total) : 0);

        return stats;
    }

    /**
     * 获取单个卡口的实时统计数据
     * @param checkpointId 卡口ID或Code
     */
    public Map<String, Object> getCheckpointStats(String checkpointId) {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime dayStart = simNow.toLocalDate().atStartOfDay();
        LocalDateTime hourStart = simNow.minusHours(1);

        // 今日通行量
        Long todayTotal = passRecordRepository.countByCheckpointIdInRange(checkpointId, dayStart, simNow);
        // 最近1小时通行量（用于计算实时车流）
        Long hourlyCount = passRecordRepository.countByCheckpointIdInRange(checkpointId, hourStart, simNow);
        // 本地/外地车辆
        Long localCount = passRecordRepository.countLocalByCheckpointIdInRange(checkpointId, dayStart, simNow);
        Long foreignCount = passRecordRepository.countForeignByCheckpointIdInRange(checkpointId, dayStart, simNow);

        long today = todayTotal != null ? todayTotal : 0L;
        long hourly = hourlyCount != null ? hourlyCount : 0L;
        long localVehicles = localCount != null ? localCount : 0L;
        long foreignVehicles = foreignCount != null ? foreignCount : 0L;

        // 计算状态：根据小时流量判断拥堵程度
        String status = "normal";
        if (hourly > 200) {
            status = "congested";
        } else if (hourly > 100) {
            status = "busy";
        }

        stats.put("checkpointId", checkpointId);
        stats.put("todayTotal", today);
        stats.put("hourlyFlow", hourly);
        stats.put("localCount", localVehicles);
        stats.put("foreignCount", foreignVehicles);
        stats.put("localRate", today > 0 ? Math.round(localVehicles * 100.0 / today) : 0);
        stats.put("foreignRate", today > 0 ? Math.round(foreignVehicles * 100.0 / today) : 0);
        stats.put("status", status);

        return stats;
    }

    /**
     * 获取区域热度排名统计
     * 按行政区划统计通行量并排名，对比前一时段计算趋势
     * @param timeRange "hour" 表示最近1小时，"day" 表示今日累计
     */
    public List<Map<String, Object>> getRegionHeatStats(String timeRange) {
        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime currentStart;
        LocalDateTime prevStart;
        LocalDateTime prevEnd;

        if ("hour".equalsIgnoreCase(timeRange)) {
            // 最近1小时 vs 前1小时
            currentStart = simNow.minusHours(1);
            prevStart = simNow.minusHours(2);
            prevEnd = simNow.minusHours(1);
        } else {
            // 今日累计 vs 昨日同时段
            currentStart = simNow.toLocalDate().atStartOfDay();
            prevStart = currentStart.minusDays(1);
            prevEnd = simNow.minusDays(1);
        }

        // 当前时段数据
        List<Object[]> currentResults = passRecordRepository.countByRegionInRange(currentStart, simNow);

        // 前一时段数据（用于计算趋势）
        List<Object[]> prevResults = passRecordRepository.countByRegionInRange(prevStart, prevEnd);

        // 区域名称规范化映射（处理可能的数据不一致）
        Map<String, String> regionNormalize = Map.of(
            "睢宁", "睢宁县",
            "铜山", "铜山区",
            "沛", "沛县",
            "新沂", "新沂市",
            "邳州", "邳州市",
            "贾汪", "贾汪区"
        );

        // 构建前一时段数据映射
        Map<String, Long> prevMap = new HashMap<>();
        for (Object[] row : prevResults) {
            if (row[0] != null) {
                String region = normalizeRegionName(String.valueOf(row[0]), regionNormalize);
                long count = ((Number) row[1]).longValue();
                prevMap.merge(region, count, Long::sum);
            }
        }

        return currentResults.stream()
            .filter(row -> row[0] != null)
            .map(row -> {
                String region = normalizeRegionName(String.valueOf(row[0]), regionNormalize);
                long count = ((Number) row[1]).longValue();
                
                // 计算趋势：(当前 - 前一时段) / 前一时段 * 100，取整
                long prevCount = prevMap.getOrDefault(region, 0L);
                int trend = 0;
                if (prevCount > 0) {
                    trend = (int) Math.round((count - prevCount) * 100.0 / prevCount);
                } else if (count > 0) {
                    trend = 100; // 前一时段无数据，当前有数据，视为100%增长
                }
                
                Map<String, Object> item = new HashMap<>();
                item.put("region", region);
                item.put("count", count);
                item.put("trend", trend);
                return item;
            })
            .toList();
    }

    /**
     * 规范化区域名称
     */
    private String normalizeRegionName(String region, Map<String, String> normalizeMap) {
        for (Map.Entry<String, String> entry : normalizeMap.entrySet()) {
            if (region.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return region;
    }

    /**
     * 获取套牌车检测列表
     */
    public Page<ClonePlateDetection> getClonePlates(String status, String plateNumber, LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        // Sort by the simulated event time.
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "time2"));
        String normalizedStatus = normalizeClonePlateStatus(status);
        return clonePlateRepository.search(normalizedStatus, plateNumber, startTime, endTime, pageable);
    }

    /**
     * 获取违规记录列表
     */
    public Page<Violation> getViolations(String type, String status, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "violationTime"));
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
     * 检测车流量高峰告警 - 返回超过阈值的卡口列表
     * @param threshold 阈值比例（0-1），默认0.7表示超过最大容量70%时触发告警
     */
    public Map<String, Object> detectFlowAlerts(double threshold) {
        Map<String, Long> flowPerHour = getCheckpointFlowPerHour();
        List<Checkpoint> checkpoints = checkpointRepository.findAll();
        LocalDateTime simNow = timeService.getSimulatedTime();

        // 筛选超过阈值的卡口
        List<Map<String, Object>> alerts = checkpoints.stream()
                .map(cp -> {
                    long currentFlow = flowPerHour.getOrDefault(cp.getCode(), 0L);
                    int maxCapacity = cp.getLaneCount() != null ? cp.getLaneCount() * 800 : 3200;
                    double ratio = maxCapacity <= 0 ? 0 : (double) currentFlow / (double) maxCapacity;
                    return Map.of(
                            "checkpoint", cp,
                            "currentFlow", currentFlow,
                            "maxCapacity", maxCapacity,
                            "ratio", ratio
                    );
                })
                .filter(m -> (double) m.get("ratio") >= threshold)
                .map(m -> {
                    Checkpoint cp = (Checkpoint) m.get("checkpoint");
                    long currentFlow = (long) m.get("currentFlow");
                    int maxCapacity = (int) m.get("maxCapacity");
                    double ratio = (double) m.get("ratio");
                    
                    String level = ratio >= 0.9 ? "critical" : (ratio >= 0.8 ? "danger" : "warning");
                    String message = String.format("%s 车流量达到 %d%%，当前 %d 辆/小时，建议及时分流",
                            CheckpointCatalog.displayName(cp.getCode(), cp.getName()),
                            Math.round(ratio * 100),
                            currentFlow);
                    
                    return Map.<String, Object>of(
                            "id", "pressure_" + cp.getId() + "_" + System.currentTimeMillis(),
                            "checkpointId", cp.getCode(),
                            "checkpointName", CheckpointCatalog.displayName(cp.getCode(), cp.getName()),
                            "type", "pressure",
                            "level", level,
                            "currentFlow", currentFlow,
                            "maxCapacity", maxCapacity,
                            "ratio", Math.round(ratio * 100),
                            "message", message,
                            "time", simNow.toString()
                    );
                })
                .sorted((a, b) -> Long.compare((long) b.get("currentFlow"), (long) a.get("currentFlow")))
                .toList();

        return Map.of(
                "hasAlerts", !alerts.isEmpty(),
                "alertCount", alerts.size(),
                "alerts", alerts,
                "checkTime", simNow.toString()
        );
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
                    .findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "violationTime")))
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
                    .findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "time2")))
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
	                event.put("time", c.getTime2() != null ? c.getTime2() : c.getCreateTime());
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

                            LocalDateTime detectTime = e.getTime2() != null ? e.getTime2() : e.getCreateTime();
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
