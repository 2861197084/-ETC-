package com.etc.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套牌车检测实体
 */
@Data
@Entity
@Table(name = "clone_plate_detection")
public class ClonePlateDetection {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "plate_number")
    private String plateNumber;
    
    @Column(name = "detection_time")
    private LocalDateTime detectionTime;
    
    @Column(name = "checkpoint1_id")
    private Long checkpoint1Id;
    
    @Column(name = "checkpoint1_name")
    private String checkpoint1Name;
    
    @Column(name = "checkpoint1_time")
    private LocalDateTime checkpoint1Time;
    
    @Column(name = "checkpoint2_id")
    private Long checkpoint2Id;
    
    @Column(name = "checkpoint2_name")
    private String checkpoint2Name;
    
    @Column(name = "checkpoint2_time")
    private LocalDateTime checkpoint2Time;
    
    private BigDecimal distance;
    
    @Column(name = "time_diff")
    private Integer timeDiff;
    
    @Column(name = "calculated_speed")
    private BigDecimal calculatedSpeed;
    
    private BigDecimal confidence;
    
    private Integer status;
}
