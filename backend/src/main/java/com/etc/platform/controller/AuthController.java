package com.etc.platform.controller;

import com.etc.platform.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简单认证接口（用于前后端联调，返回模拟数据）
 *
 * 前端对应：
 * - POST /api/auth/login
 * - GET  /api/user/info
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    /**
     * 登录接口：目前直接返回固定 token，忽略用户名密码，用于联调。
     */
    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody(required = false) Map<String, Object> params) {
        Map<String, Object> data = new HashMap<>();
        data.put("token", "mock-token-123456");
        data.put("refreshToken", "mock-refresh-123456");
        data.put("expires", Instant.now().plusSeconds(3600).toEpochMilli());
        // 前端通常会在登录响应里取用户基础信息或在后续 /user/info 获取
        return ApiResponse.ok(data);
    }

    /**
     * 用户信息接口：提供角色等信息，供前端路由守卫和菜单权限使用。
     */
    @GetMapping("/user/info")
    public ApiResponse<Map<String, Object>> userInfo() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("username", "admin");
        data.put("nickname", "ETC 管理员");
        data.put("avatar", "https://dummy.example.com/avatar.png");
        // 角色与权限可以按前端期望调整，这里先给一个 admin 角色和全权限
        data.put("roles", List.of("admin"));
        data.put("permissions", List.of("*:*:*"));
        return ApiResponse.ok(data);
    }
}


