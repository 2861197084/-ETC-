package com.etc.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卡口信息实体
 */
@Data
@Entity
@Table(name = "checkpoint")
public class Checkpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String district;

    @Column(columnDefinition = "VARCHAR(64) DEFAULT '徐州市'")
    private String city;

    @Column(columnDefinition = "VARCHAR(64) DEFAULT '江苏省'")
    private String province;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(columnDefinition = "VARCHAR(32) DEFAULT '双向'")
    private String direction;

    private String roadName;

    @Column(columnDefinition = "INT DEFAULT 4")
    private Integer laneCount;

    @Column(columnDefinition = "VARCHAR(32) DEFAULT '省界卡口'")
    private String type;

    @Column(columnDefinition = "TINYINT DEFAULT 1")
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
