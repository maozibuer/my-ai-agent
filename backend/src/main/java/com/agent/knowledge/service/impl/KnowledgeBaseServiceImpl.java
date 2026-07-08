package com.agent.knowledge.service.impl;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.agent.common.BusinessException;
import com.agent.common.PageResult;
import com.agent.common.ResultCode;
import com.agent.knowledge.entity.KnowledgeBase;
import com.agent.knowledge.entity.KnowledgeDocument;
import com.agent.knowledge.mapper.KnowledgeDocumentMapper;
import com.agent.knowledge.service.DocumentChunker;
import com.agent.knowledge.service.DocumentParser;
import com.agent.knowledge.service.ElasticsearchVectorStore;
import com.agent.knowledge.service.EmbeddingService;
import com.agent.knowledge.service.HybridSearchService;
import com.agent.knowledge.service.HybridSearchService.SearchResult;
import com.agent.knowledge.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link KnowledgeBaseService}.
 * Orchestrates document parsing, chunking, embedding, storage, search, and deletion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final ElasticsearchVectorStore elasticsearchVectorStore;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final com.agent.knowledge.mapper.KnowledgeBaseMapper knowledgeBaseMapper;
    private final HybridSearchService hybridSearchService;

    /**
     * Self-reference injected by Spring so that calls to {@code saveDocumentRecord}
     * and {@code updateDocumentStatus} go through the proxy and honour
     * {@code REQUIRES_NEW} propagation.
     */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private KnowledgeBaseServiceImpl self;

    /** Default Elasticsearch index name for knowledge base documents */
    private static final String ES_INDEX = "kb_documents";

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadDocument(MultipartFile file, Long knowledgeBaseId) {
        log.info("Uploading document: {}, kbId={}", file.getOriginalFilename(), knowledgeBaseId);

        // Validate knowledgeBaseId is provided and exists
        if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, 
                    "知识库ID不能为空，请选择要上传到的知识库。");
        }

        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null || kb.getDeleted()) {
            throw new BusinessException(ResultCode.NOT_FOUND, 
                    "指定的知识库不存在或已被删除: " + knowledgeBaseId);
        }

        String fileName = file.getOriginalFilename();
        long   fileSize = file.getSize();
        String fileType = extractExtension(fileName);
        long   kbId     = knowledgeBaseId;

        // ── Duplicate detection ────────────────────────────────────────────────
        // Read the file bytes once — needed for both hash and later parsing.
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法读取文件内容: " + e.getMessage());
        }

        String contentHash = computeMd5(fileBytes);

        // 1. Same content hash in the same KB → exact duplicate (even if renamed)
        Long existingByHash = knowledgeDocumentMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getContentHash, contentHash)
                        .eq(KnowledgeDocument::getKnowledgeBaseId, kbId)
                        .eq(KnowledgeDocument::getDeleted, false));
        if (existingByHash > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "文件已存在于该知识库中（内容重复），请勿重复上传。");
        }

        // 2. Same file name + same size in the same KB → likely duplicate
        Long existingByName = knowledgeDocumentMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getFileName, fileName)
                        .eq(KnowledgeDocument::getFileSize, fileSize)
                        .eq(KnowledgeDocument::getKnowledgeBaseId, kbId)
                        .eq(KnowledgeDocument::getDeleted, false));
        if (existingByName > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "文件「" + fileName + "」已存在于该知识库中，请勿重复上传。");
        }
        // ── End duplicate detection ────────────────────────────────────────────

        KnowledgeDocument doc = self.saveDocumentRecord(fileName, fileType, fileSize, kbId, contentHash);

        try {
            // Stage 1: Parse (use pre-read bytes to avoid re-reading the stream)
            String text = documentParser.parseBytes(fileBytes, fileName);
            log.info("Parsed document {}: {} characters", fileName, text.length());

            // Stage 2: Chunk
            List<String> chunks = documentChunker.chunk(text);
            log.info("Chunked document {} into {} segments", fileName, chunks.size());

            // Stage 3: Embed
            List<float[]> vectors = embeddingService.embedBatch(chunks);
            log.info("Generated {} embeddings for document {}", vectors.size(), fileName);

            // Stage 4: Ensure ES index exists
            elasticsearchVectorStore.createIndexIfNotExists(ES_INDEX);

            // Stage 5: Store in ES
            String docIdStr = String.valueOf(doc.getId());
            for (int i = 0; i < chunks.size(); i++) {
                String chunkId = docIdStr + "_" + i + "_" + UUID.randomUUID();
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("fileName", fileName);
                metadata.put("knowledgeBaseId", kbId);
                metadata.put("docId", docIdStr);
                metadata.put("chunkIndex", i);
                elasticsearchVectorStore.store(ES_INDEX, chunkId, chunks.get(i),
                        vectors.get(i), metadata);
            }

            self.updateDocumentStatus(doc.getId(), "PROCESSED", chunks.size());
            log.info("Document {} processed successfully: {} chunks stored", fileName, chunks.size());

        } catch (BusinessException be) {
            self.updateDocumentStatus(doc.getId(), "FAILED", 0);
            throw be;
        } catch (Exception e) {
            log.error("Failed to process document {}", fileName, e);
            self.updateDocumentStatus(doc.getId(), "FAILED", 0);
            throw new BusinessException(ResultCode.KB_SEARCH_ERROR,
                    "Failed to process document: " + e.getMessage());
        }
    }

    /**
     * Computes the MD5 hex digest of the given bytes.
     */
    private String computeMd5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            log.warn("MD5 computation failed, returning empty hash", e);
            return "";
        }
    }

    /**
     * Saves the initial document record (PENDING) in a dedicated REQUIRES_NEW
     * transaction so it is committed immediately, independent of any outer transaction.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public KnowledgeDocument saveDocumentRecord(String fileName, String fileType,
                                                long fileSize, long knowledgeBaseId,
                                                String contentHash) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setFileSize(fileSize);
        doc.setContentHash(contentHash);
        doc.setStatus("PENDING");
        doc.setDeleted(false);
        knowledgeDocumentMapper.insert(doc);
        return doc;
    }

    /**
     * Updates a document's status in a dedicated REQUIRES_NEW transaction so the
     * update is always committed even when called from within a rolled-back transaction.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateDocumentStatus(Long docId, String status, int chunkCount) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(docId);
        update.setStatus(status);
        if (chunkCount > 0) {
            update.setChunkCount(chunkCount);
        }
        knowledgeDocumentMapper.updateById(update);
        log.debug("Document {} status → {}", docId, status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SearchResult> search(String query, int topK, Long kbId) {
        return hybridSearchService.search(query, topK, kbId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long docId) {
        log.info("Deleting document: {}", docId);

        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Document not found: " + docId);
        }

        // Delete from Elasticsearch by query (all chunks belonging to this document)
        try {
            elasticsearchVectorStore.deleteByDocId(ES_INDEX, String.valueOf(docId));
            log.info("Deleted ES documents for docId={}", docId);
        } catch (Exception e) {
            log.warn("Failed to delete ES documents for docId={}: {}", docId, e.getMessage());
        }

        // Delete from database (logical delete via MyBatis-Plus)
        knowledgeDocumentMapper.deleteById(docId);
        log.info("Deleted document metadata for docId={}", docId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult listDocuments(int page, int size, Long kbId) {
        Page<KnowledgeDocument> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocument::getDeleted, false);
        if (kbId != null) {
            wrapper.eq(KnowledgeDocument::getKnowledgeBaseId, kbId);
        }
        wrapper.orderByDesc(KnowledgeDocument::getCreateTime);

        Page<KnowledgeDocument> result = knowledgeDocumentMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(),
                result.getCurrent(), result.getSize());
    }

    /**
     * Extracts the file extension from a file name.
     *
     * @param fileName the file name
     * @return the extension without the dot, or "unknown" if not determinable
     */
    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
