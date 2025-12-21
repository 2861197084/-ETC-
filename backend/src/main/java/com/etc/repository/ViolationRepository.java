package com.etc.repository;

import com.etc.entity.Violation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ViolationRepository extends JpaRepository<Violation, Long> {

    Page<Violation> findByViolationType(String type, Pageable pageable);

    Page<Violation> findByStatus(String status, Pageable pageable);

    @Query("SELECT COUNT(v) FROM Violation v WHERE DATE(v.createTime) = CURRENT_DATE")
    Long countToday();

    @Query("SELECT COUNT(v) FROM Violation v WHERE v.createTime >= :start AND v.createTime < :end")
    Long countByCreateTimeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
