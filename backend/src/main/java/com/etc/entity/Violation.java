package com.etc.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 违规记录
 */
@Data
@Entity
@Table(name = "violation")
public class Violation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String plateNumber;

    @Column(nullable = false)
    private String violationType;

    private String checkpointId;
    private String checkpointName;

    private LocalDateTime violationTime;

    private BigDecimal fineAmount;

    @Column(columnDefinition = "VARCHAR(20) DEFAULT 'pending'")
    private String status;

    private LocalDateTime createTime;
}
