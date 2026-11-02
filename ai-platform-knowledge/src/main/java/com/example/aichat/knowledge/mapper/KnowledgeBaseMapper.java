package com.example.aichat.knowledge.mapper;

import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KnowledgeBaseMapper {
    List<KnowledgeBaseRecord> findPageByUserId(@Param("userId") Long userId,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    long countByUserId(@Param("userId") Long userId);

    KnowledgeBaseRecord findById(@Param("id") Long id);

    KnowledgeBaseRecord findOwnedActive(@Param("userId") Long userId, @Param("id") Long id);

    long countByEmbeddingModelId(@Param("modelId") Long modelId);

    Long insert(@Param("userId") Long userId, @Param("name") String name,
                @Param("description") String description,
                @Param("embeddingModelId") Long embeddingModelId,
                @Param("embeddingModelVersion") Integer embeddingModelVersion);

    int update(@Param("id") Long id, @Param("name") String name,
               @Param("description") String description);

    int updatePendingEmbeddingModel(@Param("id") Long id,
                                    @Param("embeddingModelId") Long embeddingModelId,
                                    @Param("embeddingModelVersion") Integer embeddingModelVersion,
                                    @Param("newIndexVersion") Long newIndexVersion);

    int switchActiveEmbedding(@Param("id") Long id, @Param("indexVersion") Long indexVersion);

    int switchActiveIndexVersion(@Param("id") Long id, @Param("indexVersion") Long indexVersion);

    int markDeleted(@Param("userId") Long userId, @Param("id") Long id);

    Long nextIndexVersion(@Param("knowledgeBaseId") Long knowledgeBaseId);
}
