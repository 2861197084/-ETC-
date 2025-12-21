package com.etc.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套牌检测记录
 */
@Data
@Entity
@Table(name = "clone_plate_detection")
public class ClonePlateDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String plateNumber;

    @Column(name = "checkpoint_id_1")
    private String checkpointId1;

    @Column(name = "checkpoint_id_2")
    private String checkpointId2;

    @Column(name = "time_1")
    private LocalDateTime time1;

    @Column(name = "time_2")
    private LocalDateTime time2;

    private BigDecimal distanceKm;
    private Integer timeDiffMinutes;
    private BigDecimal minSpeedRequired;
    private BigDecimal confidenceScore;

    @Column(columnDefinition = "VARCHAR(20) DEFAULT 'pending'")
    private String status;

    private LocalDateTime createTime;
}
