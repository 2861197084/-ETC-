package com.etc.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 违章记录实体
 */
@Data
@Entity
@Table(name = "violation")
public class Violation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "plate_number")
    private String plateNumber;
    
    @Column(name = "checkpoint_id")
    private Long checkpointId;
    
    @Column(name = "violation_type")
    private String violationType;
    
    @Column(name = "violation_time")
    private LocalDateTime violationTime;
    
    private String description;
    
    @Column(name = "fine_amount")
    private BigDecimal fineAmount;
    
    private Integer points;
    
    private Integer status;
}
