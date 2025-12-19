package com.etc.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卡口实体类
 */
@Data
@Entity
@Table(name = "checkpoint")
public class Checkpoint {
    
    @Id
    private Long id;
    
    @Column(name = "name", length = 100, nullable = false)
    private String name;
    
    @Column(name = "code", length = 50, unique = true)
    private String code;
    
    @Column(name = "type", length = 20)
    private String type;  // provincial/municipal
    
    @Column(name = "province", length = 20)
    private String province;
    
    @Column(name = "city", length = 20)
    private String city;
    
    @Column(name = "district", length = 20)
    private String district;
    
    @Column(name = "longitude", precision = 10, scale = 6)
    private BigDecimal longitude;
    
    @Column(name = "latitude", precision = 10, scale = 6)
    private BigDecimal latitude;
    
    @Column(name = "direction", length = 50)
    private String direction;
    
    @Column(name = "road_name", length = 50)
    private String roadName;
    
    @Column(name = "lane_count")
    private Integer laneCount;
    
    @Column(name = "status")
    private Integer status;  // 0-停用, 1-正常, 2-维护中
    
    @Column(name = "create_time")
    private LocalDateTime createTime;
    
    // 非持久化字段 - 用于API返回
    @Transient
    private Integer currentFlow;  // 当前流量
    
    @Transient
    private String statusText;  // 状态文字: normal/busy/congested
    
    /**
     * 获取边界类型（根据名称推断）
     */
    @Transient
    public String getBoundary() {
        if (name == null) return "unknown";
        if (name.contains("苏皖界")) return "苏皖界";
        if (name.contains("苏鲁界")) return "苏鲁界";
        if (name.contains("连云港界")) return "连云港界";
        if (name.contains("宿迁界")) return "宿迁界";
        return "unknown";
    }
}
