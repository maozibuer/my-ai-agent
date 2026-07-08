package com.agent.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for performing hybrid (BM25 + vector) search across the knowledge base.
 * Embeds the query, delegates to ElasticsearchVectorStore for hybrid retrieval,
 * and returns ranked search results.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final EmbeddingService embeddingService;
    private final ElasticsearchVectorStore elasticsearchVectorStore;

    /** Default Elasticsearch index name for knowledge base documents */
    private static final String DEFAULT_INDEX = "kb_documents";

    /**
     * Performs a hybrid search for the given query.
     *
     * @param query           the user's search query
     * @param topK            the number of top results to return
     * @param knowledgeBaseId optional knowledge base ID to scope the search; if null, searches all
     * @return a list of ranked search results
     */
    public List<SearchResult> search(String query, int topK, Long knowledgeBaseId) {
        log.info("Performing hybrid search: query='{}', topK={}, kbId={}", query, topK, knowledgeBaseId);

        float[] queryVector = embeddingService.embed(query);

        elasticsearchVectorStore.createIndexIfNotExists(DEFAULT_INDEX);

        // Pass knowledgeBaseId so ES filters to the correct KB
        List<Map<String, Object>> rawResults = elasticsearchVectorStore.search(
                DEFAULT_INDEX, query, queryVector, topK, knowledgeBaseId);

        List<SearchResult> results = new ArrayList<>();
        for (Map<String, Object> raw : rawResults) {
            SearchResult result = new SearchResult();
            result.setFileName(getStringField(raw, "fileName"));
            result.setContent(getStringField(raw, "content"));
            result.setScore(getDoubleField(raw, "score"));
            results.add(result);
        }

        log.info("Hybrid search returned {} results for kbId={}", results.size(), knowledgeBaseId);
        return results;
    }

    /**
     * Safely extracts a String field from a raw result map.
     *
     * @param map the raw result map
     * @param key the field key
     * @return the string value, or null if not present
     */
    private String getStringField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Safely extracts a Double field from a raw result map.
     *
     * @param map the raw result map
     * @param key the field key
     * @return the double value, or 0.0 if not present
     */
    private Double getDoubleField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return value != null ? Double.parseDouble(value.toString()) : 0.0;
    }

    /**
     * Represents a single search result from the hybrid search.
     */
    @lombok.Data
    public static class SearchResult {
        /** Name of the source file */
        private String fileName;

        /** Content snippet from the matched document */
        private String content;

        /** Relevance score (fused RRF score) */
        private Double score;
    }
}
