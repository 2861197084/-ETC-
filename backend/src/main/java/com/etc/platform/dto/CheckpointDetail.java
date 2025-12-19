package com.etc.platform.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * 卡口详情DTO - 包含实时流量数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointDetail {
    private String id;
    private String code;
    private String name;
    private String fullName;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String region;
    private String road;
    private String boundary;
    private String type;
    private String status;
    private Integer maxCapacity;
    
    // 实时数据
    private Integer currentFlow;      // 当前流量
    private Integer todayTotal;       // 今日总量
    private BigDecimal avgSpeed;      // 平均速度
    
    // 流量历史
    private List<Integer> todayFlow;  // 今日每小时流量
}
