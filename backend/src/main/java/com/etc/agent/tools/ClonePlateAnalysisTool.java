package com.etc.agent.tools;

import com.etc.common.CheckpointCatalog;
import com.etc.entity.ClonePlateDetection;
import com.etc.repository.ClonePlateDetectionRepository;
import com.etc.service.TimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 套牌车分析工具 - 提供套牌嫌疑检测和分析能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClonePlateAnalysisTool {

    private final ClonePlateDetectionRepository clonePlateRepository;
    private final TimeService timeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool(description = "查询套牌车嫌疑记录列表，可按车牌号、状态筛选。返回最新的检测记录。")
    public String getClonePlateRecords(
            @ToolParam(description = "车牌号(可选，支持模糊匹配)", required = false) String plateNumber,
            @ToolParam(description = "状态: 'pending'(待处理), 'confirmed'(已确认), 'dismissed'(已排除)", required = false) String status,
            @ToolParam(description = "返回记录数量，默认10条", required = false) Integer limit) {
        
        log.info("[Agent Tool] 调用 getClonePlateRecords, plateNumber={}, status={}", plateNumber, status);
        
        try {
            int pageSize = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;
            PageRequest pageRequest = PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "time2"));
            
            Page<ClonePlateDetection> page;
            if (plateNumber != null && !plateNumber.isBlank() && status != null && !status.isBlank()) {
                page = clonePlateRepository.findByPlateNumberContainingAndStatus(plateNumber, status, pageRequest);
            } else if (plateNumber != null && !plateNumber.isBlank()) {
                page = clonePlateRepository.findByPlateNumberContaining(plateNumber, pageRequest);
            } else if (status != null && !status.isBlank()) {
                page = clonePlateRepository.findByStatus(status, pageRequest);
            } else {
                page = clonePlateRepository.findAll(pageRequest);
            }
            
            List<ClonePlateDetection> records = page.getContent();
            
            if (records.isEmpty()) {
                return "未找到匹配的套牌嫌疑记录。";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("【套牌嫌疑记录】共 %d 条（总计 %d 条）\n\n", records.size(), page.getTotalElements()));
            
            for (int i = 0; i < records.size(); i++) {
                ClonePlateDetection record = records.get(i);
                sb.append(String.format("%d. 车牌: %s\n", i + 1, record.getPlateNumber()));
                sb.append(String.format("   📍 第一次: %s @ %s\n", 
                    formatCheckpoint(record.getCheckpointId1()), formatTime(record.getTime1())));
                sb.append(String.format("   📍 第二次: %s @ %s\n", 
                    formatCheckpoint(record.getCheckpointId2()), formatTime(record.getTime2())));
                sb.append(String.format("   ⏱️ 时间差: %d 秒\n", calcTimeDiffSeconds(record)));
                sb.append(String.format("   📏 距离: %.2f km\n", 
                    record.getDistanceKm() != null ? record.getDistanceKm().doubleValue() : 0));
                sb.append(String.format("   🚀 最低所需时速: %.1f km/h\n", 
                    record.getMinSpeedRequired() != null ? record.getMinSpeedRequired().doubleValue() : 0));
                sb.append(String.format("   📋 状态: %s\n\n", translateStatus(record.getStatus())));
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("查询套牌记录失败", e);
            return "查询套牌记录失败: " + e.getMessage();
        }
    }

    @Tool(description = "分析指定套牌嫌疑记录，给出专业判定意见和处理建议")
    public String analyzeClonePlate(
            @ToolParam(description = "套牌检测记录ID") Long recordId) {
        
        log.info("[Agent Tool] 调用 analyzeClonePlate, recordId={}", recordId);
        
        if (recordId == null) {
            return "请提供套牌检测记录ID";
        }
        
        try {
            Optional<ClonePlateDetection> recordOpt = clonePlateRepository.findById(recordId);
            if (recordOpt.isEmpty()) {
                return "未找到ID为 " + recordId + " 的套牌检测记录";
            }
            
            ClonePlateDetection record = recordOpt.get();
            
            StringBuilder sb = new StringBuilder();
            sb.append("【套牌嫌疑分析报告】\n\n");
            sb.append("═══════════════════════════════════════\n");
            sb.append(String.format("🚗 车牌号: %s\n", record.getPlateNumber()));
            sb.append(String.format("📋 当前状态: %s\n", translateStatus(record.getStatus())));
            sb.append("═══════════════════════════════════════\n\n");
            
            // 时空信息
            sb.append("📍 时空信息:\n");
            sb.append(String.format("   第一次出现: %s\n", formatCheckpoint(record.getCheckpointId1())));
            sb.append(String.format("   时间: %s\n", formatTime(record.getTime1())));
            sb.append(String.format("   第二次出现: %s\n", formatCheckpoint(record.getCheckpointId2())));
            sb.append(String.format("   时间: %s\n\n", formatTime(record.getTime2())));
            
            // 计算分析
            long timeDiff = calcTimeDiffSeconds(record);
            double distance = record.getDistanceKm() != null ? record.getDistanceKm().doubleValue() : 0;
            double speed = record.getMinSpeedRequired() != null ? record.getMinSpeedRequired().doubleValue() : 0;
            
            sb.append("📊 数据分析:\n");
            sb.append(String.format("   两次出现时间差: %d 秒 (%.1f 分钟)\n", timeDiff, timeDiff / 60.0));
            sb.append(String.format("   两卡口直线距离: %.2f km\n", distance));
            sb.append(String.format("   需达到的时速: %.1f km/h\n\n", speed));
            
            // 合理性判定
            sb.append("⚖️ 合理性判定:\n");
            
            String riskLevel;
            String verdict;
            List<String> reasons = new java.util.ArrayList<>();
            
            if (speed > 300) {
                riskLevel = "🔴 极高风险";
                verdict = "极大概率为套牌车辆";
                reasons.add("计算时速超过300km/h，远超任何合法车辆可能达到的速度");
                reasons.add("物理上不可能在该时间内完成该距离的移动");
            } else if (speed > 200) {
                riskLevel = "🟠 高风险";
                verdict = "很可能为套牌车辆";
                reasons.add("计算时速超过200km/h，超过高速公路最高限速");
                reasons.add("即使最高速行驶也难以在该时间内完成移动");
            } else if (speed > 150) {
                riskLevel = "🟡 中等风险";
                verdict = "存在套牌嫌疑，需进一步核实";
                reasons.add("计算时速超过150km/h，需要全程超速才能实现");
                reasons.add("建议调取视频监控进一步确认");
            } else if (speed > 120) {
                riskLevel = "🟢 低风险";
                verdict = "套牌可能性较低";
                reasons.add("计算时速在120-150km/h范围，存在超速但理论上可实现");
                reasons.add("可能是正常车辆超速行驶");
            } else {
                riskLevel = "⚪ 可能误报";
                verdict = "不太可能是套牌";
                reasons.add("计算时速在合理范围内");
                reasons.add("可能是系统检测误差或数据问题");
            }
            
            sb.append(String.format("   风险等级: %s\n", riskLevel));
            sb.append(String.format("   判定结论: %s\n", verdict));
            sb.append("   判定理由:\n");
            for (String reason : reasons) {
                sb.append(String.format("   • %s\n", reason));
            }
            
            // 处理建议
            sb.append("\n💡 处理建议:\n");
            if (speed > 200) {
                sb.append("   1. 立即标记为高度嫌疑，加入重点监控名单\n");
                sb.append("   2. 调取两卡口的视频监控核实车辆外观\n");
                sb.append("   3. 若确认套牌，通知路面执法单位拦截查验\n");
                sb.append("   4. 录入套牌车辆数据库，联动全市卡口布控\n");
            } else if (speed > 150) {
                sb.append("   1. 调取视频监控进行人工核实\n");
                sb.append("   2. 对比两次过车的车辆特征(颜色、车型、年检标志等)\n");
                sb.append("   3. 若特征一致则可能是超速，若不一致则确认套牌\n");
            } else {
                sb.append("   1. 可暂时标记为低风险或排除\n");
                sb.append("   2. 如有其他佐证信息可进一步核实\n");
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("分析套牌记录失败", e);
            return "分析套牌记录失败: " + e.getMessage();
        }
    }

    @Tool(description = "获取今日套牌检测汇总统计")
    public String getTodayClonePlateStats() {
        log.info("[Agent Tool] 调用 getTodayClonePlateStats");
        
        try {
            LocalDateTime simNow = timeService.getSimulatedTime();
            LocalDateTime dayStart = simNow.toLocalDate().atStartOfDay();
            
            Long totalCount = clonePlateRepository.countByTime2Range(dayStart, simNow);
            Long pendingCount = clonePlateRepository.countByStatusAndTime2Range("pending", dayStart, simNow);
            Long confirmedCount = clonePlateRepository.countByStatusAndTime2Range("confirmed", dayStart, simNow);
            Long dismissedCount = clonePlateRepository.countByStatusAndTime2Range("dismissed", dayStart, simNow);
            
            long total = totalCount != null ? totalCount : 0;
            long pending = pendingCount != null ? pendingCount : 0;
            long confirmed = confirmedCount != null ? confirmedCount : 0;
            long dismissed = dismissedCount != null ? dismissedCount : 0;
            
            StringBuilder sb = new StringBuilder();
            sb.append("【今日套牌检测统计】\n\n");
            sb.append(String.format("📊 检测总数: %d 起\n", total));
            sb.append(String.format("⏳ 待处理: %d 起\n", pending));
            sb.append(String.format("✅ 已确认: %d 起\n", confirmed));
            sb.append(String.format("❌ 已排除: %d 起\n", dismissed));
            
            if (pending > 0) {
                sb.append(String.format("\n⚠️ 有 %d 起待处理的套牌嫌疑，请及时核实处理。", pending));
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("获取套牌统计失败", e);
            return "获取套牌统计失败: " + e.getMessage();
        }
    }

    private String formatTime(LocalDateTime time) {
        return time != null ? time.format(TIME_FMT) : "未知";
    }

    private String translateStatus(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "pending" -> "待处理";
            case "confirmed" -> "已确认为套牌";
            case "dismissed" -> "已排除嫌疑";
            default -> status;
        };
    }

    private static String formatCheckpoint(String checkpointId) {
        if (checkpointId == null || checkpointId.isBlank()) return "未知卡口";
        String code = checkpointId.trim().toUpperCase();
        String name = CheckpointCatalog.displayName(code, "");
        if (name == null || name.isBlank()) return code;
        return code + " (" + name + ")";
    }

    private static long calcTimeDiffSeconds(ClonePlateDetection r) {
        if (r == null) return 0;
        try {
            if (r.getTime1() != null && r.getTime2() != null) {
                return Math.abs(Duration.between(r.getTime1(), r.getTime2()).getSeconds());
            }
        } catch (Exception ignored) {
            // fallback below
        }
        if (r.getTimeDiffMinutes() != null) {
            return Math.max(0L, (long) r.getTimeDiffMinutes() * 60L);
        }
        return 0;
    }
}
