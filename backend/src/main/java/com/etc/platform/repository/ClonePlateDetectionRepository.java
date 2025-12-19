package com.etc.platform.repository;

import com.etc.platform.entity.ClonePlateDetection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 套牌检测数据访问层
 */
@Repository
public interface ClonePlateDetectionRepository extends JpaRepository<ClonePlateDetection, Long> {
    
    /**
     * 按状态查询
     */
    Page<ClonePlateDetection> findByStatus(Integer status, Pageable pageable);
    
    /**
     * 按时间范围查询
     */
    Page<ClonePlateDetection> findByDetectionTimeBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    /**
     * 按车牌号查询
     */
    Page<ClonePlateDetection> findByPlateNumberContaining(String plateNumber, Pageable pageable);
    
    /**
     * 统计今日检测数量
     */
    long countByDetectionTimeAfter(LocalDateTime time);
}
