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
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PassRecordEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    
    @JsonProperty("plate_number")
    private String plateNumber;
    
    @JsonProperty("checkpoint_id")
    private Long checkpointId;
    
    @JsonProperty("checkpoint_name")
    private String checkpointName;
    
    @JsonProperty("pass_time")
    private String passTime;
    
    @JsonProperty("vehicle_type")
    private String vehicleType;
    
    @JsonProperty("vehicle_color")
    private String vehicleColor;
    
    private Integer speed;
    
    @JsonProperty("etc_card_id")
    private String etcCardId;
    
    @JsonProperty("etc_deduction")
    private BigDecimal etcDeduction;
    
    private String direction;
    
    @JsonProperty("image_url")
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
        if (checkpointId >= 1 && checkpointId <= 6) return "苏皖界";
        if (checkpointId >= 7 && checkpointId <= 12) return "苏鲁界";
        if (checkpointId >= 13 && checkpointId <= 14) return "连云港界";
        if (checkpointId >= 15 && checkpointId <= 19) return "宿迁界";
        return "未知";
    }
    
    /**
     * 是否超速(120以上)
     */
    public boolean isOverspeed() {
        return speed != null && speed > 120;
    }
}
