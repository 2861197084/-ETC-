package com.etc.platform.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 卡口流量预测DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowPrediction {
    private String checkpointId;
    private String checkpointName;
    private Integer currentFlow;
    private String predictTime;
    private List<HourlyPrediction> hourlyPredictions;
    
    // 高峰时段统计
    private Integer peakHour;
    private Integer peakFlow;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyPrediction {
        private Integer hour;         // 小时 0-23
        private String time;          // HH:mm 格式
        private Integer predictedFlow;
        private Double confidence;    // 置信度 0-1
        private String trend;         // up/down/stable
    }
}
