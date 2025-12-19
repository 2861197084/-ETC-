package com.etc.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 通行记录实体类
 */
@Data
@Entity
@Table(name = "pass_record", indexes = {
    @Index(name = "idx_plate_time", columnList = "plate_number, pass_time"),
    @Index(name = "idx_checkpoint_time", columnList = "checkpoint_id, pass_time"),
    @Index(name = "idx_pass_time", columnList = "pass_time")
})
public class PassRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "plate_number", length = 20, nullable = false)
    private String plateNumber;
    
    @Column(name = "checkpoint_id", nullable = false)
    private Long checkpointId;
    
    @Column(name = "pass_time", nullable = false)
    private LocalDateTime passTime;
    
    @Column(name = "direction", length = 10)
    private String direction;  // in/out
    
    @Column(name = "speed", precision = 5, scale = 2)
    private BigDecimal speed;
    
    @Column(name = "lane_no")
    private Integer laneNo;
    
    @Column(name = "image_url", length = 255)
    private String imageUrl;
    
    @Column(name = "vehicle_type", length = 20)
    private String vehicleType;
    
    @Column(name = "etc_deduction", precision = 10, scale = 2)
    private BigDecimal etcDeduction;
    
    @Column(name = "create_time")
    private LocalDateTime createTime;
    
    // 非持久化字段
    @Transient
    private String checkpointName;
}
