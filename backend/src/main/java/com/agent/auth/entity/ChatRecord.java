package com.agent.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * Chat record entity mapped to the {@code chat_record} table.
 * Stores individual conversation turns between users and the AI agent.
 */
@Data
@TableName("chat_record")
public class ChatRecord {

    /** Primary key ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** ID of the user who sent the message */
    private Long userId;

    /** Chat session identifier */
    private String sessionId;

    /** The user's message */
    private String userMessage;

    /** The AI agent's response */
    private String assistantMessage;

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
