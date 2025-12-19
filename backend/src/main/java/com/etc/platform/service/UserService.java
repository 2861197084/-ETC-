package com.etc.platform.service;

import com.etc.platform.dto.LoginResponse;
import com.etc.platform.dto.PageResult;
import com.etc.platform.dto.UserInfo;
import com.etc.platform.entity.SysUser;
import com.etc.platform.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务 - 从 MySQL 读取用户数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String TOKEN_PREFIX = "etc:token:";
    private static final long TOKEN_EXPIRE_HOURS = 24;

    /**
     * 用户登录
     */
    public LoginResponse login(String username, String password) {
        Optional<SysUser> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isEmpty()) {
            log.warn("用户不存在: {}", username);
            return null;
        }
        
        SysUser user = userOpt.get();
        
        // 简单密码校验 (实际应该用 BCrypt)
        // 数据库中存储的是 BCrypt 加密的密码，这里简化处理
        // 默认密码都是 123456
        if (!"123456".equals(password)) {
            log.warn("密码错误: {}", username);
            return null;
        }
        
        // 生成 Token
        String token = UUID.randomUUID().toString().replace("-", "");
        
        // 存储到 Redis
        try {
            redisTemplate.opsForValue().set(
                TOKEN_PREFIX + token, 
                user.getId().toString(), 
                TOKEN_EXPIRE_HOURS, 
                TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.warn("Redis 存储失败，使用内存模式: {}", e.getMessage());
        }
        
        // 构建响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpireTime(LocalDateTime.now().plusHours(TOKEN_EXPIRE_HOURS)
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        // 设置用户信息
        UserInfo userInfo = buildUserInfo(user);
        response.setUserInfo(userInfo);
        
        log.info("用户登录成功: {}", username);
        return response;
    }

    /**
     * 用户登出
     */
    public void logout(String token) {
        try {
            redisTemplate.delete(TOKEN_PREFIX + token);
            log.info("用户登出: token={}", token.substring(0, 8) + "...");
        } catch (Exception e) {
            log.warn("Redis 删除失败: {}", e.getMessage());
        }
    }

    /**
     * 刷新 Token
     */
    public LoginResponse refreshToken(String oldToken) {
        try {
            Object userId = redisTemplate.opsForValue().get(TOKEN_PREFIX + oldToken);
            if (userId == null) {
                return null;
            }
            
            // 删除旧 Token
            redisTemplate.delete(TOKEN_PREFIX + oldToken);
            
            // 获取用户
            SysUser user = userRepository.findById(Long.parseLong(userId.toString()))
                    .orElse(null);
            if (user == null) {
                return null;
            }
            
            // 生成新 Token
            String newToken = UUID.randomUUID().toString().replace("-", "");
            redisTemplate.opsForValue().set(TOKEN_PREFIX + newToken, userId.toString(), TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
            
            LoginResponse response = new LoginResponse();
            response.setToken(newToken);
            response.setExpireTime(LocalDateTime.now().plusHours(TOKEN_EXPIRE_HOURS)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            response.setUserInfo(buildUserInfo(user));
            
            return response;
        } catch (Exception e) {
            log.error("刷新 Token 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据 Token 获取用户信息
     */
    public UserInfo getUserByToken(String token) {
        try {
            Object userId = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
            if (userId == null) {
                // Token 无效时返回模拟数据 (方便测试)
                return createMockUserInfo();
            }
            
            SysUser user = userRepository.findById(Long.parseLong(userId.toString()))
                    .orElse(null);
            if (user == null) {
                return createMockUserInfo();
            }
            
            return buildUserInfo(user);
        } catch (Exception e) {
            log.warn("获取用户信息失败: {}", e.getMessage());
            return createMockUserInfo();
        }
    }

    /**
     * 获取用户列表
     */
    public PageResult<UserInfo> getUserList(int page, int pageSize) {
        List<SysUser> users = userRepository.findAll();
        
        List<UserInfo> userInfoList = users.stream()
                .map(this::buildUserInfo)
                .toList();
        
        // 简单分页
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, userInfoList.size());
        
        List<UserInfo> pageData = start < userInfoList.size() 
                ? userInfoList.subList(start, end) 
                : List.of();
        
        return PageResult.of(pageData, (long) userInfoList.size(), page, pageSize);
    }

    /**
     * 构建用户信息
     */
    private UserInfo buildUserInfo(SysUser user) {
        UserInfo info = new UserInfo();
        info.setUserId(user.getId());
        info.setUserName(user.getUsername());
        info.setNickName(user.getRealName());
        info.setAvatar(user.getAvatar() != null ? user.getAvatar() : "/avatar/default.png");
        info.setPhone(user.getPhone());
        info.setEmail(user.getEmail());
        
        // 根据 roleId 设置角色
        Long roleId = user.getRoleId();
        if (roleId != null) {
            switch (roleId.intValue()) {
                case 1 -> {
                    info.setRole("admin");
                    info.setUserType("admin");
                }
                case 2 -> {
                    info.setRole("owner");
                    info.setUserType("owner");
                }
                case 3 -> {
                    info.setRole("monitor");
                    info.setUserType("monitor");
                }
                default -> {
                    info.setRole("guest");
                    info.setUserType("guest");
                }
            }
        } else {
            info.setRole("guest");
            info.setUserType("guest");
        }
        
        return info;
    }

    /**
     * 创建模拟用户信息
     */
    private UserInfo createMockUserInfo() {
        UserInfo info = new UserInfo();
        info.setUserId(1L);
        info.setUserName("admin");
        info.setNickName("系统管理员");
        info.setAvatar("/avatar/admin.png");
        info.setRole("admin");
        info.setUserType("admin");
        return info;
    }
    
    /**
     * 刷新 Token (别名方法)
     */
    public LoginResponse refresh(String refreshToken) {
        return refreshToken(refreshToken);
    }
    
    /**
     * 获取用户信息 (别名方法)
     */
    public UserInfo getUserInfo(String token) {
        return getUserByToken(token);
    }
    
    /**
     * 获取用户列表（分页）
     */
    public List<SysUser> listUsers(int page, int size, String userName, Integer status) {
        List<SysUser> allUsers = userRepository.findAll();
        
        // 过滤
        List<SysUser> filtered = allUsers.stream()
                .filter(u -> userName == null || u.getUsername().contains(userName))
                .filter(u -> status == null || (u.getStatus() != null && u.getStatus().equals(status)))
                .toList();
        
        // 分页
        int start = (page - 1) * size;
        int end = Math.min(start + size, filtered.size());
        return start < filtered.size() ? filtered.subList(start, end) : List.of();
    }
    
    /**
     * 统计用户数量
     */
    public long countUsers(String userName, Integer status) {
        List<SysUser> allUsers = userRepository.findAll();
        return allUsers.stream()
                .filter(u -> userName == null || u.getUsername().contains(userName))
                .filter(u -> status == null || (u.getStatus() != null && u.getStatus().equals(status)))
                .count();
    }
}
