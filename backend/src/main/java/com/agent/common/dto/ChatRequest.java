package com.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for chat requests sent to the AI agent.
 */
@Data
public class ChatRequest {

    @NotBlank(message = "Message cannot be blank")
    private String message;

    /** Session identifier for conversation continuity */
    private String sessionId;

    /** Optional knowledge base identifier for RAG-based answers */
    private String knowledgeBaseId;
}
