package com.etc.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 通行记录实体
 */
@Data
@Entity
@Table(name = "pass_record")
public class PassRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 过车序号 */
    private String gcxh;

    /** 行政区划名称 */
    private String xzqhmc;

    /** 卡口名称 */
    private String kkmc;

    /** 方向类型 (进城/出城) */
    private String fxlx;

    /** 过车时间 */
    private LocalDateTime gcsj;

    /** 号牌种类 */
    private String hpzl;

    /** 车牌号 */
    private String hp;

    /** 车辆品牌型号 */
    private String clppxh;

    /** 分片键 */
    private Integer plateHash;

    /** 卡口ID */
    private String checkpointId;

    /**
     * 兼容：MySQL 视图 pass_record 不包含 create_time 字段。
     * 如需创建时间请使用 gcsj 或在底层表添加列。
     */
    @Transient
    private LocalDateTime createTime;
}
