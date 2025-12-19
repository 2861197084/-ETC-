package com.etc.platform.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统角色实体
 */
@Data
public class SysRole {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
}
