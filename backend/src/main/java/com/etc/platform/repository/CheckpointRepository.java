package com.etc.platform.repository;

import com.etc.platform.entity.Checkpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 卡口数据访问层
 */
@Repository
public interface CheckpointRepository extends JpaRepository<Checkpoint, Long> {
    
    /**
     * 按区县统计卡口数量
     */
    @Query("SELECT c.district, COUNT(c) FROM Checkpoint c GROUP BY c.district")
    List<Object[]> countByDistrict();
    
    /**
     * 按状态查询卡口
     */
    List<Checkpoint> findByStatus(Integer status);
    
    /**
     * 按类型查询卡口
     */
    List<Checkpoint> findByType(String type);
    
    /**
     * 按城市查询卡口
     */
    List<Checkpoint> findByCity(String city);
}
