package com.example.aichat.knowledge.mapper;

import com.example.aichat.knowledge.model.KnowledgeChunkEmbeddingRecord;
import com.example.aichat.knowledge.model.KnowledgeChunkSearchResult;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KnowledgeChunkEmbeddingMapper {
    int insertAll(@Param("embeddings") List<KnowledgeChunkEmbeddingRecord> embeddings);

    int deleteByDocument(@Param("documentId") Long documentId);

    int deleteByDocumentAndIndexVersion(@Param("documentId") Long documentId,
                                        @Param("indexVersion") Long indexVersion);

    int deleteByIndexVersion(@Param("knowledgeBaseId") Long knowledgeBaseId,
                             @Param("indexVersion") Long indexVersion);

    List<KnowledgeChunkSearchResult> search(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("modelId") Long modelId,
            @Param("dimension") Integer dimension,
            @Param("indexVersion") Long indexVersion,
            @Param("queryVector") float[] queryVector,
            @Param("topK") int topK);
}
