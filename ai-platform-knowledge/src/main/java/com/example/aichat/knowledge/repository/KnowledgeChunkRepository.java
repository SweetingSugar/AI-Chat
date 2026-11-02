package com.example.aichat.knowledge.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.aichat.knowledge.mapper.KnowledgeChunkMapper;
import com.example.aichat.knowledge.mapper.KnowledgeChunkRow;
import com.example.aichat.knowledge.model.KnowledgeChunkRecord;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class KnowledgeChunkRepository {
    private final KnowledgeChunkMapper mapper;
    private final ObjectMapper objectMapper;

    public KnowledgeChunkRepository(KnowledgeChunkMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public List<KnowledgeChunkRecord> findByDocumentId(Long documentId) {
        return mapper.findByDocumentId(documentId).stream().map(this::toRecord).toList();
    }

    public List<KnowledgeChunkRecord> findByDocumentIdAndIndexVersion(Long documentId, Long indexVersion) {
        return mapper.findByDocumentIdAndIndexVersion(documentId, indexVersion)
                .stream().map(this::toRecord).toList();
    }

    public List<KnowledgeChunkRecord> insertAll(List<KnowledgeChunkRecord> chunks) {
        return chunks.stream().map(this::insert).toList();
    }

    public KnowledgeChunkRecord insert(KnowledgeChunkRecord chunk) {
        Long id = mapper.insert(chunk.documentId(), chunk.indexVersion(), chunk.chunkIndex(),
                chunk.content(), chunk.estimatedTokens(), writeMetadata(chunk.metadata()));
        return new KnowledgeChunkRecord(id, chunk.documentId(), chunk.chunkIndex(), chunk.content(),
                chunk.estimatedTokens(), chunk.metadata(), chunk.indexVersion());
    }

    public int deleteByDocumentId(Long documentId) {
        return mapper.deleteByDocumentId(documentId);
    }

    public int deleteByDocumentAndIndexVersion(Long documentId, Long indexVersion) {
        return mapper.deleteByDocumentAndIndexVersion(documentId, indexVersion);
    }

    private KnowledgeChunkRecord toRecord(KnowledgeChunkRow row) {
        return new KnowledgeChunkRecord(row.id(), row.documentId(), row.chunkIndex(), row.content(),
                row.estimatedTokens(), readMetadata(row.metadataJson()), row.indexVersion());
    }

    private Map<String, Object> readMetadata(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize chunk metadata", exception);
        }
    }
}
