package com.etc.platform.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 热力图数据点DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapPoint {
    private Double lng;
    private Double lat;
    private Integer count;
}
