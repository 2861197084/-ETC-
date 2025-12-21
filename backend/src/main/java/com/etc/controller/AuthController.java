package com.etc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "认证接口", description = "用户认证相关接口")
public class AuthController {

    /** 统一成功响应 */
    private Map<String, Object> success(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "success");
        response.put("data", data);
        return response;
    }

    /** 统一失败响应 */
    private Map<String, Object> error(int code, String msg) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("msg", msg);
        response.put("data", null);
        return response;
    }

    @PostMapping("/auth/login")
    @Operation(summary = "用户登录")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        // 支持 userName 和 username 两种字段名
        String username = request.get("userName");
        if (username == null) {
            username = request.get("username");
        }
        String password = request.get("password");

        // 验证账户 (支持多种测试账户)
        boolean valid = false;
        if ("admin".equalsIgnoreCase(username) && ("admin123".equals(password) || "123456".equals(password))) {
            valid = true;
        } else if ("Admin".equals(username) && "123456".equals(password)) {
            valid = true;
        } else if ("User".equals(username) && "123456".equals(password)) {
            valid = true;
        }

        if (valid) {
            Map<String, Object> data = new HashMap<>();
            data.put("token", UUID.randomUUID().toString());
            data.put("refreshToken", UUID.randomUUID().toString());
            data.put("expiresIn", 86400);
            return ResponseEntity.ok(success(data));
        }

        return ResponseEntity.status(401).body(error(401, "用户名或密码错误"));
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "用户登出")
    public ResponseEntity<Map<String, Object>> logout() {
        return ResponseEntity.ok(success(Map.of("success", true)));
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "刷新Token")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, String> request) {
        Map<String, Object> data = new HashMap<>();
        data.put("token", UUID.randomUUID().toString());
        data.put("refreshToken", UUID.randomUUID().toString());
        data.put("expiresIn", 86400);
        return ResponseEntity.ok(success(data));
    }

    @GetMapping("/user/info")
    @Operation(summary = "获取用户信息")
    public ResponseEntity<Map<String, Object>> getUserInfo() {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", 1);
        userInfo.put("userName", "admin");
        userInfo.put("nickname", "管理员");
        userInfo.put("avatar", "");
        userInfo.put("roles", List.of("admin"));
        userInfo.put("buttons", List.of("*"));
        userInfo.put("email", "admin@etc.com");
        return ResponseEntity.ok(success(userInfo));
    }
}
