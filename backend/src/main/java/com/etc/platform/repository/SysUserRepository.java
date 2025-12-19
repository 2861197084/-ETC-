package com.etc.platform.repository;

import com.etc.platform.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层
 */
@Repository
public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    
    /**
     * 按用户名查询
     */
    Optional<SysUser> findByUsername(String username);
    
    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 按手机号查询
     */
    Optional<SysUser> findByPhone(String phone);
}
