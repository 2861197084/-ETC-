package com.etc.platform.repository;

import com.etc.platform.entity.Violation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 违章记录数据访问层
 */
@Repository
public interface ViolationRepository extends JpaRepository<Violation, Long> {
    
    /**
     * 按违章类型查询
     */
    Page<Violation> findByViolationType(String violationType, Pageable pageable);
    
    /**
     * 按时间范围查询
     */
    Page<Violation> findByViolationTimeBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    /**
     * 按车牌号查询
     */
    Page<Violation> findByPlateNumberContaining(String plateNumber, Pageable pageable);
    
    /**
     * 统计今日违章数量
     */
    long countByViolationTimeAfter(LocalDateTime time);
    
    /**
     * 按违章类型统计数量
     */
    long countByViolationType(String violationType);
}
