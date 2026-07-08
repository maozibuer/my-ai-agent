package com.agent.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.agent.common.BusinessException;
import com.agent.common.PageResult;
import com.agent.common.Result;
import com.agent.common.ResultCode;
import com.agent.common.dto.KnowledgeSearchRequest;
import com.agent.knowledge.entity.KnowledgeBase;
import com.agent.knowledge.entity.KnowledgeDocument;
import com.agent.knowledge.mapper.KnowledgeBaseMapper;
import com.agent.knowledge.mapper.KnowledgeDocumentMapper;
import com.agent.knowledge.service.HybridSearchService.SearchResult;
import com.agent.knowledge.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for knowledge base management.
 * Provides CRUD for knowledge bases (kb_base) and document management.
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Tag(name = "Knowledge Base API", description = "Knowledge base and document management")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    // =========================================================
    // Knowledge Base CRUD  (kb_base table)
    // =========================================================

    /** List all knowledge bases (non-deleted, ordered by create time desc). */
    @GetMapping("/bases")
    @Operation(summary = "List knowledge bases")
    public Result<List<KnowledgeBase>> listKnowledgeBases() {
        LambdaQueryWrapper<KnowledgeBase> qw = new LambdaQueryWrapper<>();
        qw.eq(KnowledgeBase::getDeleted, false)
          .orderByDesc(KnowledgeBase::getCreateTime);
        List<KnowledgeBase> bases = knowledgeBaseMapper.selectList(qw);

        // Populate documentCount from kb_document table for each knowledge base.
        // The upload/delete flows never update the document_count column in kb_base,
        // so we compute it dynamically — always accurate, no sync issues.
        for (KnowledgeBase kb : bases) {
            Long count = knowledgeDocumentMapper.selectCount(
                    new LambdaQueryWrapper<KnowledgeDocument>()
                            .eq(KnowledgeDocument::getKnowledgeBaseId, kb.getId())
                            .eq(KnowledgeDocument::getDeleted, false));
            kb.setDocumentCount(count);
        }

        return Result.success(bases);
    }

    /** Create a new knowledge base. */
    @PostMapping("/bases")
    @Operation(summary = "Create knowledge base")
    public Result<KnowledgeBase> createKnowledgeBase(@RequestBody KnowledgeBase kb) {
        if (kb.getName() == null || kb.getName().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "知识库名称不能为空");
        }
        kb.setDeleted(false);
        knowledgeBaseMapper.insert(kb);
        log.info("Created knowledge base: id={}, name={}", kb.getId(), kb.getName());
        return Result.success(kb);
    }

    /** Update an existing knowledge base. */
    @PutMapping("/bases/{id}")
    @Operation(summary = "Update knowledge base")
    public Result<Void> updateKnowledgeBase(@PathVariable Long id, @RequestBody KnowledgeBase kb) {
        kb.setId(id);
        kb.setDeleted(null); // never change delete flag via this endpoint
        knowledgeBaseMapper.updateById(kb);
        log.info("Updated knowledge base: id={}", id);
        return Result.success();
    }

    /** Soft-delete a knowledge base. */
    @DeleteMapping("/bases/{id}")
    @Operation(summary = "Delete knowledge base")
    public Result<Void> deleteKnowledgeBase(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在: " + id);
        }
        // Use deleteById() instead of manual setDeleted(true)+updateById().
        // MyBatis-Plus @TableLogic intercepts deleteById() and converts it to
        // UPDATE SET deleted = 1 WHERE id = ?.  Regular updateById() SILENTLY
        // drops the @TableLogic-annotated field from the generated SQL, which
        // is why the deleted flag never changed in the database.
        knowledgeBaseMapper.deleteById(id);
        log.info("Deleted knowledge base: id={}", id);
        return Result.success();
    }

    // =========================================================
    // Document management
    // =========================================================

    /** Upload a document and associate it with a knowledge base. */
    @PostMapping("/upload")
    @Operation(summary = "Upload document")
    public Result<Void> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kbId") Long kbId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (kbId == null || kbId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, 
                    "知识库ID不能为空，请选择要上传到的知识库");
        }
        log.info("Upload: file={}, kbId={}", file.getOriginalFilename(), kbId);
        knowledgeBaseService.uploadDocument(file, kbId);
        return Result.success();
    }

    /** Hybrid search across knowledge base documents. */
    @PostMapping("/search")
    @Operation(summary = "Search knowledge base")
    public Result<List<SearchResult>> search(@RequestBody KnowledgeSearchRequest request) {
        Long kbId = null;
        if (request.getKnowledgeBaseId() != null && !request.getKnowledgeBaseId().isBlank()) {
            try { kbId = Long.parseLong(request.getKnowledgeBaseId()); }
            catch (NumberFormatException ignored) {}
        }
        int topK = request.getTopK() != null ? request.getTopK() : 5;
        return Result.success(knowledgeBaseService.search(request.getQuery(), topK, kbId));
    }

    /** Delete a document by its ID. */
    @DeleteMapping("/{docId}")
    @Operation(summary = "Delete document")
    public Result<Void> deleteDocument(@PathVariable Long docId) {
        log.info("Delete document: docId={}", docId);
        knowledgeBaseService.deleteDocument(docId);
        return Result.success();
    }

    /** List documents with pagination, optional kbId filter. */
    @GetMapping("/documents")
    @Operation(summary = "List documents")
    public Result<PageResult> listDocuments(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "kbId", required = false) Long kbId) {
        return Result.success(knowledgeBaseService.listDocuments(page, size, kbId));
    }
}
