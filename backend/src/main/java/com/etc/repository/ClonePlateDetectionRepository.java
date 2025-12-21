package com.etc.repository;

import com.etc.entity.ClonePlateDetection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ClonePlateDetectionRepository extends JpaRepository<ClonePlateDetection, Long> {

    Page<ClonePlateDetection> findByStatus(String status, Pageable pageable);

    Page<ClonePlateDetection> findByPlateNumber(String plateNumber, Pageable pageable);

    @Query("SELECT COUNT(c) FROM ClonePlateDetection c WHERE c.createTime >= :start AND c.createTime < :end")
    Long countByCreateTimeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
