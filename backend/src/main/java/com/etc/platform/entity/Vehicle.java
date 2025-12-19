package com.etc.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 车辆信息实体类
 */
@Data
@Entity
@Table(name = "vehicle", indexes = {
    @Index(name = "idx_plate", columnList = "plate_number"),
    @Index(name = "idx_owner", columnList = "owner_id"),
    @Index(name = "idx_etc", columnList = "etc_card_no")
})
public class Vehicle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "plate_number", length = 20, nullable = false, unique = true)
    private String plateNumber;
    
    @Column(name = "plate_color", length = 10)
    private String plateColor;  // 蓝/黄/绿/白/黑
    
    @Column(name = "vehicle_type", length = 20)
    private String vehicleType;
    
    @Column(name = "owner_id")
    private Long ownerId;
    
    @Column(name = "owner_name", length = 50)
    private String ownerName;
    
    @Column(name = "owner_phone", length = 20)
    private String ownerPhone;
    
    @Column(name = "etc_card_no", length = 30)
    private String etcCardNo;
    
    @Column(name = "etc_status")
    private Integer etcStatus;  // 0-未绑定, 1-正常, 2-挂失, 3-注销
    
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
