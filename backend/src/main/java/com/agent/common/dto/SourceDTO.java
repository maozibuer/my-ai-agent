package com.agent.common.dto;

import lombok.Data;

/**
 * DTO representing a source document used in RAG-based answer generation.
 */
@Data
public class SourceDTO {

    /** Name of the source file */
    private String fileName;

    /** Content snippet from the source document */
    private String content;

    /** Relevance score of the source document */
    private Double score;
}
