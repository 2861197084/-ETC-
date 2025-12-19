package com.etc.platform.repository;

import com.etc.platform.entity.CheckpointFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 卡口流量统计数据访问层
 */
@Repository
public interface CheckpointFlowRepository extends JpaRepository<CheckpointFlow, Long> {
    
    /**
     * 查询某卡口某天的流量统计
     */
    List<CheckpointFlow> findByCheckpointIdAndStatDate(Long checkpointId, LocalDate statDate);
    
    /**
     * 查询某天所有卡口的流量统计
     */
    List<CheckpointFlow> findByStatDate(LocalDate statDate);
    
    /**
     * 查询某卡口某天某小时的流量
     */
    @Query("SELECT cf FROM CheckpointFlow cf WHERE cf.checkpointId = :checkpointId AND cf.statDate = :date AND cf.statHour = :hour")
    CheckpointFlow findByCheckpointAndDateAndHour(@Param("checkpointId") Long checkpointId,
                                                   @Param("date") LocalDate date,
                                                   @Param("hour") Integer hour);
    
    /**
     * 统计某天的总流量
     */
    @Query("SELECT SUM(cf.totalCount) FROM CheckpointFlow cf WHERE cf.statDate = :date AND cf.statHour IS NULL")
    Long sumTotalFlowByDate(@Param("date") LocalDate date);
}
