package com.etc.platform.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 市内监测站点实体 - 用于路径规划
 */
@Data
public class Station {
    private String id;
    private String code;           // 站点编号
    private String name;           // 站点名称
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String region;         // 所属区县
    private String roadLevel;      // 道路等级: highway/national/provincial/county
    private String roadName;       // 道路名称
    private String direction;      // 方向: both/inbound/outbound
    private String status;         // online/offline/maintenance
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
