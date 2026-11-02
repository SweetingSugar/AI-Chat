package com.example.aichat.knowledge.repository;

import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.enums.KnowledgeDocumentStatus;
import com.example.aichat.knowledge.mapper.KnowledgeDocumentMapper;
import com.example.aichat.knowledge.model.KnowledgeDocumentRecord;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeDocumentRepository {
    private final KnowledgeDocumentMapper mapper;

    public KnowledgeDocumentRepository(KnowledgeDocumentMapper mapper) {
        this.mapper = mapper;
    }

    public List<KnowledgeDocumentRecord> findPageByKnowledgeBaseId(Long knowledgeBaseId, int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        return mapper.findPageByKnowledgeBaseId(knowledgeBaseId, size, offset);
    }

    public List<KnowledgeDocumentRecord> findByKnowledgeBaseId(Long knowledgeBaseId) {
        return mapper.findByKnowledgeBaseId(knowledgeBaseId);
    }

    public long countByKnowledgeBaseId(Long knowledgeBaseId) {
        return mapper.countByKnowledgeBaseId(knowledgeBaseId);
    }

    public Optional<KnowledgeDocumentRecord> findById(Long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public Optional<KnowledgeDocumentRecord> findActiveByKnowledgeBaseAndFile(Long knowledgeBaseId, Long fileId) {
        return Optional.ofNullable(mapper.findActiveByKnowledgeBaseAndFile(knowledgeBaseId, fileId));
    }

    public Long insert(Long knowledgeBaseId, KnowledgeDocumentSourceType sourceType, Long fileId,
                       String title, String rawContent, Long indexVersion) {
        return mapper.insert(knowledgeBaseId, sourceType.name(), fileId, title, rawContent, indexVersion);
    }

    public int markIndexing(Long documentId, Long indexVersion) {
        return mapper.markIndexing(documentId, indexVersion);
    }

    public int markReady(Long documentId, Long indexVersion, int chunkCount) {
        return mapper.markReady(documentId, indexVersion, chunkCount);
    }

    public int updateContentHash(Long documentId, String contentHash) {
        return mapper.updateContentHash(documentId, contentHash);
    }

    public int markFailed(Long documentId, String errorCode, String errorMessage, boolean preserveReady) {
        String status = preserveReady ? KnowledgeDocumentStatus.READY.name() : KnowledgeDocumentStatus.FAILED.name();
        return mapper.markFailed(documentId, status, errorCode, errorMessage);
    }

    public int markDeleted(Long id) {
        return mapper.markDeleted(id);
    }
}
