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

    Page<ClonePlateDetection> findByPlateNumberContaining(String plateNumber, Pageable pageable);

    Page<ClonePlateDetection> findByPlateNumberContainingAndStatus(String plateNumber, String status, Pageable pageable);

    @Query("SELECT COUNT(c) FROM ClonePlateDetection c WHERE c.time2 >= :start AND c.time2 < :end")
    Long countByTime2Range(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(c) FROM ClonePlateDetection c WHERE c.status = :status AND c.time2 >= :start AND c.time2 < :end")
    Long countByStatusAndTime2Range(@Param("status") String status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT c FROM ClonePlateDetection c
            WHERE (:status IS NULL OR :status = '' OR c.status = :status)
              AND (:plate IS NULL OR :plate = '' OR c.plateNumber LIKE CONCAT('%', :plate, '%'))
              AND (:start IS NULL OR c.time2 >= :start)
              AND (:end IS NULL OR c.time2 < :end)
            """)
    Page<ClonePlateDetection> search(
            @Param("status") String status,
            @Param("plate") String plate,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);
}
