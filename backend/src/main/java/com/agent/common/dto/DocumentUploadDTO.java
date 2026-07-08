package com.agent.common.dto;

import lombok.Data;

/**
 * DTO for document upload metadata.
 */
@Data
public class DocumentUploadDTO {

    /** Name of the uploaded file */
    private String fileName;

    /** File type (e.g., pdf, txt, docx) */
    private String fileType;

    /** File size in bytes */
    private Long fileSize;

    /** Target knowledge base identifier */
    private String knowledgeBaseId;
}
