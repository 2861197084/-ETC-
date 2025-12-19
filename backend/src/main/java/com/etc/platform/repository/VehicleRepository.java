package com.etc.platform.repository;

import com.etc.platform.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 车辆数据访问层
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    
    /**
     * 按车牌号查询
     */
    Optional<Vehicle> findByPlateNumber(String plateNumber);
    
    /**
     * 按车主ID查询
     */
    List<Vehicle> findByOwnerId(Long ownerId);
    
    /**
     * 按ETC卡号查询
     */
    Optional<Vehicle> findByEtcCardNo(String etcCardNo);
    
    /**
     * 检查车牌号是否存在
     */
    boolean existsByPlateNumber(String plateNumber);
}
