package com.etc.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 卡口流量统计实体类
 */
@Data
@Entity
@Table(name = "checkpoint_flow", indexes = {
    @Index(name = "idx_date", columnList = "stat_date")
})
public class CheckpointFlow {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "checkpoint_id", nullable = false)
    private Long checkpointId;
    
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;
    
    @Column(name = "stat_hour")
    private Integer statHour;  // 0-23, NULL表示全天
    
    @Column(name = "total_count")
    private Integer totalCount;
    
    @Column(name = "in_count")
    private Integer inCount;
    
    @Column(name = "out_count")
    private Integer outCount;
    
    @Column(name = "avg_speed", precision = 5, scale = 2)
    private BigDecimal avgSpeed;
    
    @Column(name = "peak_count")
    private Integer peakCount;
    
    @Column(name = "peak_time")
    private LocalTime peakTime;
    
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
