package com.etc.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 通行记录事件 - 从Kafka接收的数据
 * 字段映射：
 * - hp -> plateNumber (车牌号)
 * - gcsj -> passTime (过车时间)
 * - checkpointId -> checkpointIdStr (卡口编号，如 CP001)
 * - kkmc -> checkpointName (卡口名称)
 * - fxlx -> direction (方向类型：进城/出城)
 * - hpzl -> vehicleType (号牌种类)
 * - clppxh -> vehicleModel (车辆品牌型号)
 * - xzqhmc -> regionName (行政区划名称)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PassRecordEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    
    // 过车序号
    private String gcxh;
    
    // 车牌号 (hp = 号牌)
    @JsonProperty("hp")
    private String plateNumber;
    
    // 卡口编号 (如 CP001)
    private String checkpointId;
    
    // 卡口名称
    @JsonProperty("kkmc")
    private String checkpointName;
    
    // 过车时间
    @JsonProperty("gcsj")
    private String passTime;
    
    // 号牌种类
    @JsonProperty("hpzl")
    private String vehicleType;
    
    // 车辆品牌型号
    @JsonProperty("clppxh")
    private String vehicleModel;
    
    // 方向类型
    @JsonProperty("fxlx")
    private String direction;
    
    // 行政区划名称
    @JsonProperty("xzqhmc")
    private String regionName;
    
    // 事件时间戳（毫秒）
    private Long eventTime;
    
    // 以下字段保留兼容性，可能不存在于Kafka消息中
    private String vehicleColor;
    private Integer speed;
    private String etcCardId;
    private BigDecimal etcDeduction;
    private String imageUrl;
    private String timestamp;
    
    /**
     * 判断是否为本地车辆（苏C开头）
     */
    public boolean isLocalVehicle() {
        return plateNumber != null && plateNumber.startsWith("苏C");
    }
    
    /**
     * 获取区域（根据卡口ID判断）
     */
    public String getRegion() {
        if (checkpointId == null) return "未知";
        // checkpointId 格式如 "CP001", 提取数字部分
        try {
            int cpNum = Integer.parseInt(checkpointId.replaceAll("[^0-9]", ""));
            if (cpNum >= 1 && cpNum <= 6) return "苏皖界";
            if (cpNum >= 7 && cpNum <= 12) return "苏鲁界";
            if (cpNum >= 13 && cpNum <= 14) return "连云港界";
            if (cpNum >= 15 && cpNum <= 19) return "宿迁界";
        } catch (NumberFormatException e) {
            return "未知";
        }
        return "未知";
    }
    
    /**
     * 获取卡口ID的数字部分（用于兼容原有Long类型逻辑）
     * 如 "CP001" -> 1, "CP015" -> 15
     */
    public Long getCheckpointIdNum() {
        if (checkpointId == null) return null;
        try {
            return Long.parseLong(checkpointId.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 是否超速(120以上)
     */
    public boolean isOverspeed() {
        return speed != null && speed > 120;
    }
}
