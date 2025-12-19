package com.etc.platform.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 申诉实体
 */
@Data
public class Appeal {
    private String id;
    private Long userId;
    private String alertId;
    private String appealType;        // clone_plate/violation/fee/other
    private String reason;
    private List<String> proofImages;
    private String status;            // pending/approved/rejected
    private String result;
    private String handler;
    private LocalDateTime handleTime;
    private LocalDateTime createdAt;
}
