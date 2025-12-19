package com.etc.platform.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
public class LoginRequest {
    @JsonAlias({"username", "userName"})
    private String userName;
    private String password;
}
