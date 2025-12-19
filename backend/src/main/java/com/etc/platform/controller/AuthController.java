package com.etc.platform.controller;

import com.etc.platform.dto.ApiResponse;
import com.etc.platform.dto.LoginRequest;
import com.etc.platform.dto.LoginResponse;
import com.etc.platform.dto.UserInfo;
import com.etc.platform.dto.PageResult;
import com.etc.platform.entity.SysUser;
import com.etc.platform.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户认证接口
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 登录接口
     */
    @PostMapping("/auth/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request.getUserName(), request.getPassword());
        if (response == null) {
            return ApiResponse.error("用户名或密码错误", 401);
        }
        return ApiResponse.ok(response);
    }

    /**
     * 登出接口
     */
    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) {
            userService.logout(token.substring(7));
        }
        return ApiResponse.ok(null);
    }

    /**
     * 刷新Token
     */
    @PostMapping("/auth/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody Map<String, String> params) {
        String refreshToken = params.get("refreshToken");
        LoginResponse response = userService.refresh(refreshToken);
        return ApiResponse.ok(response);
    }

    /**
     * 用户信息接口
     */
    @GetMapping("/user/info")
    public ApiResponse<UserInfo> userInfo(@RequestHeader(value = "Authorization", required = false) String token) {
        String actualToken = token != null && token.startsWith("Bearer ") ? token.substring(7) : null;
        UserInfo info = userService.getUserInfo(actualToken);
        if (info == null) {
            return ApiResponse.error("用户不存在", 404);
        }
        return ApiResponse.ok(info);
    }

    /**
     * 用户列表
     */
    @GetMapping("/user/list")
    public ApiResponse<PageResult<SysUser>> userList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Integer status) {
        List<SysUser> list = userService.listUsers(current, size, userName, status);
        long total = userService.countUsers(userName, status);
        return ApiResponse.ok(PageResult.of(list, total, current, size));
    }

    /**
     * 角色列表（Mock）
     */
    @GetMapping("/role/list")
    public ApiResponse<PageResult<Map<String, Object>>> roleList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        List<Map<String, Object>> roles = List.of(
                Map.of("id", 1, "roleCode", "admin", "roleName", "管理员", "status", 1),
                Map.of("id", 2, "roleCode", "user", "roleName", "普通用户", "status", 1)
        );
        return ApiResponse.ok(PageResult.of(roles, 2L, current, size));
    }

    /**
     * 系统菜单（Mock）
     */
    @GetMapping("/v3/system/menus")
    public ApiResponse<List<Map<String, Object>>> menus() {
        // 返回空列表，让前端使用本地路由
        return ApiResponse.ok(List.of());
    }
}



