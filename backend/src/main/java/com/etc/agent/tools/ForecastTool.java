package com.etc.agent.tools;

import com.etc.service.ForecastService;
import com.etc.service.TimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 预测分析工具 - 提供车流量预测查询能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastTool {

    private final ForecastService forecastService;
    private final TimeService timeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_MODEL_VERSION = "time-moe-5m";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Tool(description = "查询指定卡口的车流量预测结果，返回未来1小时(12个5分钟间隔)的预测车流量。fxlx参数: 'up'(上行/进城)或'down'(下行/出城)")
    public String getForecast(
            @ToolParam(description = "卡口ID，如CP001、CP002等") String checkpointId,
            @ToolParam(description = "方向: 'up'(上行/进城)或'down'(下行/出城)", required = false) String fxlx) {
        
        log.info("[Agent Tool] 调用 getForecast, checkpointId={}, fxlx={}", checkpointId, fxlx);
        
        if (checkpointId == null || checkpointId.isBlank()) {
            return "请提供卡口ID，如CP001";
        }
        
        String direction = (fxlx == null || fxlx.isBlank()) ? "up" : fxlx.toLowerCase();
        if (!direction.equals("up") && !direction.equals("down")) {
            direction = "up";
        }
        
        try {
            Optional<ForecastService.ForecastRow> forecastOpt = 
                forecastService.findLatestForecast(checkpointId, direction, DEFAULT_MODEL_VERSION);
            
            if (forecastOpt.isEmpty()) {
                return String.format("暂无卡口 %s (%s方向) 的预测数据。请先在预测分析页面触发预测请求。", 
                    checkpointId, direction.equals("up") ? "上行" : "下行");
            }
            
            ForecastService.ForecastRow forecast = forecastOpt.get();
            List<Double> values = forecast.values();
            
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("【卡口 %s %s方向 车流量预测】\n\n", 
                checkpointId, direction.equals("up") ? "上行(进城)" : "下行(出城)"));
            sb.append("📈 预测起始时间: ").append(forecast.startTime()).append("\n");
            sb.append("🔧 模型版本: ").append(forecast.modelVersion()).append("\n");
            sb.append("⏰ 更新时间: ").append(forecast.updatedAt()).append("\n\n");
            
            sb.append("📊 未来1小时车流量预测（每5分钟）:\n");
            LocalDateTime startTime = LocalDateTime.parse(forecast.startTime().replace(" ", "T"));
            
            for (int i = 0; i < values.size() && i < 12; i++) {
                LocalDateTime pointTime = startTime.plusMinutes(i * 5L);
                String timeStr = pointTime.format(TIME_FMT);
                double value = values.get(i);
                int intValue = (int) Math.round(value);
                
                // 用条形图可视化
                int barLen = Math.min(20, Math.max(1, intValue / 5));
                String bar = "█".repeat(barLen);
                
                sb.append(String.format("%s  %s %d辆\n", timeStr, bar, intValue));
            }
            
            // 总结分析
            double total = values.stream().mapToDouble(Double::doubleValue).sum();
            double avg = total / values.size();
            double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            
            sb.append("\n📋 预测汇总:\n");
            sb.append(String.format("- 预计总车流: %.0f 辆\n", total));
            sb.append(String.format("- 平均每5分钟: %.1f 辆\n", avg));
            sb.append(String.format("- 峰值: %.0f 辆，谷值: %.0f 辆\n", max, min));
            
            // 给出建议
            if (avg > 50) {
                sb.append("\n⚠️ 预测车流较大，建议提前做好通行引导准备。");
            } else if (avg < 10) {
                sb.append("\n✅ 预测车流较小，通行压力不大。");
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("获取预测数据失败", e);
            return "获取预测数据失败: " + e.getMessage();
        }
    }

    @Tool(description = "解释预测结果的含义，分析可能的影响因素")
    public String explainForecast(
            @ToolParam(description = "卡口ID") String checkpointId,
            @ToolParam(description = "方向: 'up'或'down'", required = false) String fxlx) {
        
        log.info("[Agent Tool] 调用 explainForecast, checkpointId={}", checkpointId);
        
        String direction = (fxlx == null || fxlx.isBlank()) ? "up" : fxlx.toLowerCase();
        
        try {
            Optional<ForecastService.ForecastRow> forecastOpt = 
                forecastService.findLatestForecast(checkpointId, direction, DEFAULT_MODEL_VERSION);
            
            if (forecastOpt.isEmpty()) {
                return "暂无预测数据可供解释。";
            }
            
            ForecastService.ForecastRow forecast = forecastOpt.get();
            List<Double> values = forecast.values();
            
            LocalDateTime simNow = timeService.getSimulatedTime();
            int hour = simNow.getHour();
            
            StringBuilder sb = new StringBuilder();
            sb.append("【预测结果解释】\n\n");
            
            // 分析趋势
            double firstHalf = values.subList(0, Math.min(6, values.size())).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double secondHalf = values.subList(Math.min(6, values.size()), values.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            
            if (secondHalf > firstHalf * 1.2) {
                sb.append("📈 趋势分析: 车流量呈上升趋势\n");
            } else if (secondHalf < firstHalf * 0.8) {
                sb.append("📉 趋势分析: 车流量呈下降趋势\n");
            } else {
                sb.append("➡️ 趋势分析: 车流量基本平稳\n");
            }
            
            // 时段分析
            sb.append("\n⏰ 时段因素:\n");
            if (hour >= 7 && hour <= 9) {
                sb.append("- 当前处于早高峰时段(7:00-9:00)，通勤车流较多\n");
            } else if (hour >= 17 && hour <= 19) {
                sb.append("- 当前处于晚高峰时段(17:00-19:00)，下班车流较多\n");
            } else if (hour >= 11 && hour <= 13) {
                sb.append("- 当前处于午间时段，车流相对平稳\n");
            } else if (hour >= 22 || hour <= 6) {
                sb.append("- 当前处于夜间时段，车流较少\n");
            } else {
                sb.append("- 当前处于平峰时段\n");
            }
            
            // 方向分析
            sb.append("\n🚗 方向分析:\n");
            if (direction.equals("up")) {
                sb.append("- 上行方向(进城): ");
                if (hour < 12) {
                    sb.append("早间进城方向通常车流较大\n");
                } else {
                    sb.append("下午进城方向车流相对较少\n");
                }
            } else {
                sb.append("- 下行方向(出城): ");
                if (hour >= 16) {
                    sb.append("傍晚出城方向通常车流较大\n");
                } else {
                    sb.append("上午出城方向车流相对较少\n");
                }
            }
            
            sb.append("\n💡 模型说明:\n");
            sb.append("- 预测基于 Time-MoE 深度学习模型\n");
            sb.append("- 使用历史5分钟粒度车流数据训练\n");
            sb.append("- 预测未来12个时间点(共1小时)\n");
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("解释预测失败", e);
            return "解释预测失败: " + e.getMessage();
        }
    }
}
