package com.example.aichat.knowledge.repository;

import com.example.aichat.knowledge.mapper.KnowledgeChunkEmbeddingMapper;
import com.example.aichat.knowledge.model.KnowledgeChunkEmbeddingRecord;
import com.example.aichat.knowledge.model.KnowledgeChunkSearchResult;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class KnowledgeChunkEmbeddingRepository {
    private final KnowledgeChunkEmbeddingMapper mapper;

    public KnowledgeChunkEmbeddingRepository(KnowledgeChunkEmbeddingMapper mapper) {
        this.mapper = mapper;
    }

    public int insertAll(List<KnowledgeChunkEmbeddingRecord> embeddings) {
        if (embeddings.isEmpty()) {
            return 0;
        }
        return mapper.insertAll(embeddings);
    }

    public int deleteByDocument(Long documentId) {
        return mapper.deleteByDocument(documentId);
    }

    public int deleteByDocumentAndIndexVersion(Long documentId, Long indexVersion) {
        return mapper.deleteByDocumentAndIndexVersion(documentId, indexVersion);
    }

    public int deleteByIndexVersion(Long knowledgeBaseId, Long indexVersion) {
        return mapper.deleteByIndexVersion(knowledgeBaseId, indexVersion);
    }

    public List<KnowledgeChunkSearchResult> search(Long knowledgeBaseId, Long modelId, Integer dimension,
                                                    Long indexVersion, float[] queryVector, int topK) {
        return mapper.search(knowledgeBaseId, modelId, dimension, indexVersion, queryVector, topK);
    }
}
