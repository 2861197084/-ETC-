package com.etc.repository;

import com.etc.entity.PassRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PassRecordRepository extends JpaRepository<PassRecord, Long> {

    Page<PassRecord> findByHpContaining(String plate, Pageable pageable);

    Page<PassRecord> findByCheckpointId(String checkpointId, Pageable pageable);

    Page<PassRecord> findByGcsjBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    long countByHp(String hp);

    long countByHpAndGcsjBetween(String hp, LocalDateTime start, LocalDateTime end);

    long countByCheckpointId(String checkpointId);

    long countByCheckpointIdAndGcsjBetween(String checkpointId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT p FROM PassRecord p WHERE " +
            "(:plate IS NULL OR p.hp LIKE %:plate%) AND " +
            "(:checkpointId IS NULL OR p.checkpointId = :checkpointId) AND " +
            "(:startTime IS NULL OR p.gcsj >= :startTime) AND " +
            "(:endTime IS NULL OR p.gcsj <= :endTime)")
    Page<PassRecord> search(
            @Param("plate") String plate,
            @Param("checkpointId") String checkpointId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    @Query("SELECT COUNT(p) FROM PassRecord p WHERE DATE(p.gcsj) = CURRENT_DATE")
    Long countToday();

    @Query("SELECT COUNT(p) FROM PassRecord p WHERE p.gcsj >= :start AND p.gcsj < :end")
    Long countByTimeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT p.checkpointId, COUNT(p) FROM PassRecord p WHERE p.gcsj >= :start AND p.gcsj < :end GROUP BY p.checkpointId")
    List<Object[]> countByCheckpointInRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
