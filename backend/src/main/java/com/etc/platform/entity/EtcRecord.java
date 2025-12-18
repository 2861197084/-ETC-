package com.etc.platform.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EtcRecord {

    private Long id;

    private String gantryId;
    private String plate;
    private LocalDateTime passTime;
    private Integer vehicleType;
    private Integer fee;
    private String provinceId;
}