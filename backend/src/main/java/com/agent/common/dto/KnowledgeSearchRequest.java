package com.agent.common.dto;

import lombok.Data;

/**
 * DTO for knowledge base search requests.
 */
@Data
public class KnowledgeSearchRequest {

    /** Search query string */
    private String query;

    /** Number of top results to return (default 5) */
    private Integer topK = 5;

    /** Optional knowledge base identifier to narrow the search scope */
    private String knowledgeBaseId;
}
