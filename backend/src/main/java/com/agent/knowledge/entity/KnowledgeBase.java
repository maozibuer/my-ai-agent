package com.agent.knowledge.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;

/**
 * Knowledge base entity mapped to the {@code kb_base} table.
 * Represents a logical grouping of knowledge documents.
 */
@Data
@TableName("kb_base")
public class KnowledgeBase {

    /** Primary key ID — serialised as String to avoid JS precision loss. */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** Name of the knowledge base */
    private String name;

    /** Description of the knowledge base */
    private String description;

    /** Department that owns this knowledge base */
    private String department;

    /** Number of documents in this knowledge base */
    private Long documentCount;

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
