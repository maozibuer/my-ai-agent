package com.agent.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * User entity mapped to the {@code sys_user} table.
 */
@Data
@TableName("sys_user")
public class User {

    /** Primary key ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** Username (unique) */
    private String username;

    /** BCrypt-encoded password */
    private String password;

    /** Email address */
    private String email;

    /** Avatar URL */
    private String avatar;

    /** User role */
    private Role role;

    // ===== Audit fields =====

    /** Creator of this record */
    private String creator;

    /** Creation timestamp */
    private LocalDateTime createTime;

    /** Last updater of this record */
    private String updater;

    /** Last update timestamp */
    private LocalDateTime updateTime;

    /** Logical delete flag */
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;

    /** Tenant ID for multi-tenancy */
    private Long tenantId;
}
