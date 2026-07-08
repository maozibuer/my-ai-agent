package com.agent.knowledge.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.agent.common.PageResult;
import com.agent.knowledge.service.HybridSearchService.SearchResult;

/**
 * Service interface for knowledge base management.
 * Provides methods for document upload, search, deletion, and listing.
 */
public interface KnowledgeBaseService {

    /**
     * Uploads a document to the specified knowledge base.
     * The document is parsed, chunked, embedded, and stored in Elasticsearch.
     * Metadata is persisted to the database.
     *
     * @param file            the uploaded file
     * @param knowledgeBaseId the target knowledge base ID (optional)
     */
    void uploadDocument(MultipartFile file, Long knowledgeBaseId);

    /**
     * Searches the knowledge base for relevant documents.
     *
     * @param query the search query
     * @param topK  the number of top results to return
     * @param kbId  the knowledge base ID to scope the search (optional)
     * @return a list of ranked search results
     */
    List<SearchResult> search(String query, int topK, Long kbId);

    /**
     * Deletes a document from both Elasticsearch and the database.
     *
     * @param docId the document ID to delete
     */
    void deleteDocument(Long docId);

    /**
     * Lists documents in a knowledge base with pagination.
     *
     * @param page the page number (1-based)
     * @param size the page size
     * @param kbId the knowledge base ID to filter by (optional)
     * @return a paginated result of documents
     */
    PageResult listDocuments(int page, int size, Long kbId);
}
