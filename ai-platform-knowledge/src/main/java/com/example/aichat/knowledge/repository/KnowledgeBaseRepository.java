package com.example.aichat.knowledge.repository;

import com.example.aichat.knowledge.mapper.KnowledgeBaseMapper;
import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeBaseRepository {
    private final KnowledgeBaseMapper mapper;

    public KnowledgeBaseRepository(KnowledgeBaseMapper mapper) {
        this.mapper = mapper;
    }

    public List<KnowledgeBaseRecord> findPageByUserId(Long userId, int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        return mapper.findPageByUserId(userId, size, offset);
    }

    public long countByUserId(Long userId) {
        return mapper.countByUserId(userId);
    }

    public List<KnowledgeBaseRecord> findPageAll(int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        return mapper.findPageAll(size, offset);
    }

    public long countAll() {
        return mapper.countAll();
    }

    public Optional<KnowledgeBaseRecord> findNotDeletedById(Long id) {
        return Optional.ofNullable(mapper.findNotDeletedById(id));
    }

    public Optional<KnowledgeBaseRecord> findById(Long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public Optional<KnowledgeBaseRecord> findOwnedActive(Long userId, Long id) {
        return Optional.ofNullable(mapper.findOwnedActive(userId, id));
    }

    public long countByEmbeddingModelId(Long modelId) {
        return mapper.countByEmbeddingModelId(modelId);
    }

    public Long insert(Long userId, String name, String description, Long embeddingModelId,
                       Integer embeddingModelVersion) {
        return mapper.insert(userId, name, description, embeddingModelId, embeddingModelVersion);
    }

    public int update(Long id, String name, String description) {
        return mapper.update(id, name, description);
    }

    public int updatePendingEmbeddingModel(Long id, Long embeddingModelId, Integer embeddingModelVersion,
                                           Long newIndexVersion) {
        return mapper.updatePendingEmbeddingModel(id, embeddingModelId, embeddingModelVersion, newIndexVersion);
    }

    public int switchActiveEmbedding(Long id, Long indexVersion) {
        return mapper.switchActiveEmbedding(id, indexVersion);
    }

    public int switchActiveIndexVersion(Long id, Long indexVersion) {
        return mapper.switchActiveIndexVersion(id, indexVersion);
    }

    public int markDeleted(Long userId, Long id) {
        return mapper.markDeleted(userId, id);
    }

    public long nextIndexVersion(Long knowledgeBaseId) {
        Long next = mapper.nextIndexVersion(knowledgeBaseId);
        return next == null ? 1L : next;
    }
}
