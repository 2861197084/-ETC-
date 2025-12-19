package com.etc.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警实体
 */
@Data
@Entity
@Table(name = "alert")
public class Alert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "alert_type")
    private String alertType;
    
    private String level;
    
    private String title;
    
    private String content;
    
    @Column(name = "checkpoint_id")
    private Long checkpointId;
    
    @Column(name = "plate_number")
    private String plateNumber;
    
    private Integer status;
    
    @Column(name = "handle_time")
    private LocalDateTime handleTime;
    
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
