package com.etc.platform.repository;

import com.etc.platform.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 告警数据访问层
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    
    /**
     * 按告警类型查询
     */
    Page<Alert> findByAlertType(String alertType, Pageable pageable);
    
    /**
     * 按状态查询
     */
    Page<Alert> findByStatus(Integer status, Pageable pageable);
    
    /**
     * 统计今日告警数量
     */
    long countByCreateTimeAfter(LocalDateTime time);
}
