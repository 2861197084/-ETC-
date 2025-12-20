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
 * 适配实际数据库结构（字段为拼音简写）
 */
@Repository
public interface PassRecordRepository extends JpaRepository<PassRecord, Long> {
    
    /**
     * 按车牌号查询通行记录
     */
    Page<PassRecord> findByPlateNumber(String plateNumber, Pageable pageable);
    
    /**
     * 按卡口ID字符串查询通行记录 (如 CP001)
     */
    Page<PassRecord> findByCheckpointIdStr(String checkpointIdStr, Pageable pageable);
    
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
    @Query("SELECT p.checkpointIdStr, COUNT(p) FROM PassRecord p WHERE p.passTime >= :today GROUP BY p.checkpointIdStr")
    List<Object[]> countTodayFlowByCheckpoint(@Param("today") LocalDateTime today);
    
    /**
     * 按车牌号和时间范围查询
     */
    @Query("SELECT p FROM PassRecord p WHERE p.plateNumber = :plate AND p.passTime BETWEEN :start AND :end ORDER BY p.passTime DESC")
    List<PassRecord> findByPlateAndTimeRange(@Param("plate") String plateNumber,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);
    
    /**
     * 统计某卡口某小时的流量 (使用原生SQL，字段名为gcsj)
     */
    @Query(value = "SELECT HOUR(gcsj) as hour, COUNT(*) as count FROM pass_record " +
           "WHERE checkpoint_id = :checkpointId AND DATE(gcsj) = CURDATE() " +
           "GROUP BY HOUR(gcsj) ORDER BY hour", nativeQuery = true)
    List<Object[]> countHourlyFlow(@Param("checkpointId") String checkpointId);
    
    /**
     * 按车牌号模糊查询
     */
    Page<PassRecord> findByPlateNumberContaining(String plateNumber, Pageable pageable);
    
    /**
     * 统计时间范围内的记录数
     */
    long countByPassTimeBetween(LocalDateTime start, LocalDateTime end);
    
    /**
     * 统计时间范围内的ETC扣款总额 - 原表无此字段，返回null
     */
    @Query("SELECT CAST(0 AS java.math.BigDecimal) FROM PassRecord p WHERE 1=0")
    java.math.BigDecimal sumEtcDeductionByPassTimeBetween(@Param("start") LocalDateTime start, 
                                                          @Param("end") LocalDateTime end);
    
    /**
     * 按车牌号模糊查询和时间范围
     */
    Page<PassRecord> findByPlateNumberContainingAndPassTimeBetween(String plateNumber, 
                                                                    LocalDateTime start, 
                                                                    LocalDateTime end, 
                                                                    Pageable pageable);
    
    /**
     * 按卡口ID字符串和时间范围查询
     */
    Page<PassRecord> findByCheckpointIdStrAndPassTimeBetween(String checkpointIdStr, 
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
    
    /**
     * 统计包含指定车牌号的记录数
     */
    long countByPlateNumberContaining(String plateNumber);
    
    /**
     * 统计指定卡口的记录数
     */
    long countByCheckpointIdStr(String checkpointIdStr);
}
