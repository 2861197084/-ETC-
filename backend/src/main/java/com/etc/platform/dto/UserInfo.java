package com.etc.platform.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 用户信息响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    private Long id;
    private Long userId;
    private String userName;
    private String username;
    private String nickName;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private String userType;
    private String role;
    private List<String> roles;
    private List<String> permissions;
}
