package com.etc.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 通行记录实体类
 * 映射到实际数据库表结构（字段为拼音简写）
 */
@Data
@Entity
@Table(name = "pass_record")
public class PassRecord {
    
    @Id
    private Long id;
    
    /**
     * 车牌号 (hp = 号牌)
     */
    @Column(name = "hp", length = 32)
    private String plateNumber;
    
    /**
     * 卡口ID (checkpoint_id 字符串格式如 CP001)
     */
    @Column(name = "checkpoint_id", length = 64)
    private String checkpointIdStr;
    
    /**
     * 通过时间 (gcsj = 过车时间)
     */
    @Column(name = "gcsj")
    private LocalDateTime passTime;
    
    /**
     * 方向类型 (fxlx = 方向类型)
     */
    @Column(name = "fxlx", length = 32)
    private String direction;
    
    /**
     * 车辆类型 (hpzl = 号牌种类)
     */
    @Column(name = "hpzl", length = 32)
    private String vehicleType;
    
    /**
     * 卡口名称 (kkmc = 卡口名称)
     */
    @Column(name = "kkmc", length = 128)
    private String checkpointNameDb;
    
    /**
     * 行政区划名称 (xzqhmc)
     */
    @Column(name = "xzqhmc", length = 128)
    private String regionName;
    
    /**
     * 过车序号 (gcxh)
     */
    @Column(name = "gcxh", length = 64)
    private String passSerial;
    
    /**
     * 车辆品牌型号 (clppxh)
     */
    @Column(name = "clppxh", length = 128)
    private String vehicleBrand;
    
    /**
     * 车牌哈希值
     */
    @Column(name = "plate_hash")
    private Integer plateHash;
    
    // === 兼容旧代码的字段，使用 @Transient 或计算属性 ===
    
    /**
     * 获取数字格式的卡口ID（从 CP001 提取 1）
     */
    @Transient
    public Long getCheckpointId() {
        if (checkpointIdStr != null && checkpointIdStr.startsWith("CP")) {
            try {
                return Long.parseLong(checkpointIdStr.substring(2));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    // 这些字段原表中不存在，返回默认值
    @Transient
    private BigDecimal speed;
    
    @Transient
    private Integer laneNo;
    
    @Transient
    private String imageUrl;
    
    @Transient
    private BigDecimal etcDeduction;
    
    @Transient
    private LocalDateTime createTime;
    
    @Transient
    private String checkpointName;
}
