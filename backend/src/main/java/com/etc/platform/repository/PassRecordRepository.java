package com.etc.platform.repository;

import com.etc.platform.entity.PassRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通行记录数据访问层
 */
@Repository
public interface PassRecordRepository extends JpaRepository<PassRecord, Long> {
    
    /**
     * 按车牌号查询通行记录
     */
    Page<PassRecord> findByPlateNumber(String plateNumber, Pageable pageable);
    
    /**
     * 按卡口ID查询通行记录
     */
    Page<PassRecord> findByCheckpointId(Long checkpointId, Pageable pageable);
    
    /**
     * 按时间范围查询
     */
    @Query("SELECT p FROM PassRecord p WHERE p.passTime BETWEEN :start AND :end ORDER BY p.passTime DESC")
    Page<PassRecord> findByTimeRange(@Param("start") LocalDateTime start, 
                                     @Param("end") LocalDateTime end, 
                                     Pageable pageable);
    
    /**
     * 统计卡口今日流量
     */
    @Query("SELECT p.checkpointId, COUNT(p) FROM PassRecord p WHERE p.passTime >= :today GROUP BY p.checkpointId")
    List<Object[]> countTodayFlowByCheckpoint(@Param("today") LocalDateTime today);
    
    /**
     * 按车牌号和时间范围查询
     */
    @Query("SELECT p FROM PassRecord p WHERE p.plateNumber = :plate AND p.passTime BETWEEN :start AND :end ORDER BY p.passTime DESC")
    List<PassRecord> findByPlateAndTimeRange(@Param("plate") String plateNumber,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);
    
    /**
     * 统计某卡口某小时的流量
     */
    @Query(value = "SELECT HOUR(pass_time) as hour, COUNT(*) as count FROM pass_record " +
           "WHERE checkpoint_id = :checkpointId AND DATE(pass_time) = CURDATE() " +
           "GROUP BY HOUR(pass_time) ORDER BY hour", nativeQuery = true)
    List<Object[]> countHourlyFlow(@Param("checkpointId") Long checkpointId);
    
    /**
     * 按车牌号模糊查询
     */
    Page<PassRecord> findByPlateNumberContaining(String plateNumber, Pageable pageable);
    
    /**
     * 统计时间范围内的记录数
     */
    long countByPassTimeBetween(LocalDateTime start, LocalDateTime end);
    
    /**
     * 统计时间范围内的ETC扣款总额
     */
    @Query("SELECT SUM(p.etcDeduction) FROM PassRecord p WHERE p.passTime BETWEEN :start AND :end")
    java.math.BigDecimal sumEtcDeductionByPassTimeBetween(@Param("start") LocalDateTime start, 
                                                          @Param("end") LocalDateTime end);
    
    /**
     * 按速度阈值查询超速记录
     */
    Page<PassRecord> findBySpeedGreaterThan(java.math.BigDecimal minSpeed, Pageable pageable);
    
    /**
     * 按速度和时间范围查询超速记录
     */
    Page<PassRecord> findBySpeedGreaterThanAndPassTimeBetween(java.math.BigDecimal minSpeed, 
                                                              LocalDateTime start, 
                                                              LocalDateTime end, 
                                                              Pageable pageable);
    
    /**
     * 按车牌号模糊查询和时间范围
     */
    Page<PassRecord> findByPlateNumberContainingAndPassTimeBetween(String plateNumber, 
                                                                    LocalDateTime start, 
                                                                    LocalDateTime end, 
                                                                    Pageable pageable);
    
    /**
     * 按卡口ID和时间范围查询
     */
    Page<PassRecord> findByCheckpointIdAndPassTimeBetween(Long checkpointId, 
                                                          LocalDateTime start, 
                                                          LocalDateTime end, 
                                                          Pageable pageable);
    
    /**
     * 按时间范围分页查询
     */
    Page<PassRecord> findByPassTimeBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    /**
     * 统计以指定前缀开头的车牌数量（用于本地/外地车辆统计）
     */
    @Query("SELECT COUNT(p) FROM PassRecord p WHERE p.plateNumber LIKE CONCAT(:prefix, '%') AND p.passTime >= :afterTime")
    long countByPlateNumberStartingWithAndPassTimeAfter(@Param("prefix") String prefix, @Param("afterTime") LocalDateTime afterTime);
}
