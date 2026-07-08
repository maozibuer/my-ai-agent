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
 * Knowledge document entity mapped to the {@code kb_document} table.
 * Stores metadata about documents uploaded to the knowledge base.
 */
@Data
@TableName("kb_document")
public class KnowledgeDocument {

    /**
     * Primary key ID — serialised as String to avoid JS precision loss.
     * Snowflake IDs exceed Number.MAX_SAFE_INTEGER (2^53-1) and get silently
     * rounded by the browser, breaking delete/update operations.
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** Name of the uploaded file */
    private String fileName;

    /** File type / extension (e.g., pdf, docx, txt) */
    private String fileType;

    /** ID of the knowledge base this document belongs to */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;

    /** Number of chunks the document was split into */
    private Integer chunkCount;

    /** File size in bytes */
    private Long fileSize;

    /** Processing status: PROCESSED, PENDING, or FAILED */
    private String status;

    /**
     * MD5 hash of the file content.
     * Used for duplicate detection: same hash in same KB = duplicate upload.
     */
    private String contentHash;

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
