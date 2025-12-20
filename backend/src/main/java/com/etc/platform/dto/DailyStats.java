package com.etc.platform.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 今日统计DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyStats {
    private Integer totalFlow;        // 今日总流量
    private Integer hourlyFlow;       // 最近一小时流量
    private Integer totalRevenue;     // 今日总收入（整数，元）
    private Integer hourlyRevenue;    // 最近一小时收入
    private BigDecimal avgSpeed;      // 平均速度
    private Integer onlineDevices;    // 在线设备数
    private Integer abnormalEvents;   // 异常事件数
    private Integer clonePlates;      // 疑似套牌数
    private Integer violations;       // 违规数
    
    // 额外字段
    private Integer checkpointCount;  // 卡口数量
    private Integer onlineCount;      // 在线数量
    private Integer alertCount;       // 告警数量
    private Integer abnormalCount;    // 异常数量
    private String statTime;          // 统计时间
}
