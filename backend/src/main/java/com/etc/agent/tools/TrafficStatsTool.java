package com.etc.agent.tools;

import com.etc.service.RealtimeService;
import com.etc.service.StatsReadService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 交通统计工具 - 提供实时路况和统计数据查询能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrafficStatsTool {

    private final RealtimeService realtimeService;
    private final StatsReadService statsReadService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(description = "查询今日交通统计数据，包括总通行量、告警数量、平均车速、收费站在线数等")
    public String getDailyStats() {
        log.info("[Agent Tool] 调用 getDailyStats");
        try {
            Map<String, Object> stats = realtimeService.getDailyStats();
            return formatJson(stats);
        } catch (Exception e) {
            log.error("获取今日统计失败", e);
            return "获取今日统计数据失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询车辆来源统计，区分本地车辆(苏C)和外地车辆的数量及占比")
    public String getVehicleSourceStats() {
        log.info("[Agent Tool] 调用 getVehicleSourceStats");
        try {
            Map<String, Object> stats = realtimeService.getVehicleSourceStats();
            return formatJson(stats);
        } catch (Exception e) {
            log.error("获取车辆来源统计失败", e);
            return "获取车辆来源统计失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询区域热度排名，统计各行政区的通行量并排名。timeRange参数: 'hour'表示最近1小时, 'day'表示今日累计")
    public String getRegionHeatStats(
            @ToolParam(description = "时间范围: 'hour'(最近1小时) 或 'day'(今日累计)", required = false) 
            String timeRange) {
        log.info("[Agent Tool] 调用 getRegionHeatStats, timeRange={}", timeRange);
        try {
            String range = (timeRange == null || timeRange.isBlank()) ? "hour" : timeRange;
            List<Map<String, Object>> stats = realtimeService.getRegionHeatStats(range);
            return formatJson(stats);
        } catch (Exception e) {
            log.error("获取区域热度排名失败", e);
            return "获取区域热度排名失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询指定卡口的实时统计数据，包括今日通行量、小时流量、本地/外地车辆占比、拥堵状态等")
    public String getCheckpointStats(
            @ToolParam(description = "卡口ID，如CP001、CP002等") 
            String checkpointId) {
        log.info("[Agent Tool] 调用 getCheckpointStats, checkpointId={}", checkpointId);
        try {
            if (checkpointId == null || checkpointId.isBlank()) {
                return "请提供卡口ID，如CP001";
            }
            Map<String, Object> stats = realtimeService.getCheckpointStats(checkpointId);
            return formatJson(stats);
        } catch (Exception e) {
            log.error("获取卡口统计失败", e);
            return "获取卡口统计失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询当前高速路况概况，返回所有卡口的状态(畅通/繁忙/拥堵)及建议")
    public String getCurrentTrafficOverview() {
        log.info("[Agent Tool] 调用 getCurrentTrafficOverview");
        try {
            // 获取区域热度数据
            List<Map<String, Object>> regionStats = realtimeService.getRegionHeatStats("hour");
            Map<String, Object> dailyStats = realtimeService.getDailyStats();
            
            StringBuilder sb = new StringBuilder();
            sb.append("【当前高速路况概况】\n\n");
            
            // 总体情况
            sb.append("📊 今日总通行量: ").append(dailyStats.get("totalFlow")).append(" 辆\n");
            sb.append("⚠️ 告警数量: ").append(dailyStats.get("alertCount")).append(" 起\n");
            sb.append("🚗 平均车速: ").append(dailyStats.get("avgSpeed")).append(" km/h\n\n");
            
            // 区域排名
            sb.append("📍 区域通行量排名（最近1小时）:\n");
            int rank = 1;
            for (Map<String, Object> region : regionStats) {
                String trend = "";
                Object trendObj = region.get("trend");
                if (trendObj != null) {
                    int trendVal = ((Number) trendObj).intValue();
                    if (trendVal > 0) trend = " ↑" + trendVal + "%";
                    else if (trendVal < 0) trend = " ↓" + Math.abs(trendVal) + "%";
                }
                sb.append(rank++).append(". ").append(region.get("region"))
                  .append(": ").append(region.get("count")).append(" 辆").append(trend).append("\n");
                if (rank > 5) break;
            }
            
            return sb.toString();
        } catch (Exception e) {
            log.error("获取路况概况失败", e);
            return "获取路况概况失败: " + e.getMessage();
        }
    }

    private String formatJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }
}
