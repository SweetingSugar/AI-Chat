package com.example.aichat.knowledge.mapper;

import com.example.aichat.knowledge.model.KnowledgeIndexTaskRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface KnowledgeIndexTaskMapper {
    Long insert(@Param("knowledgeBaseId") Long knowledgeBaseId,
                @Param("documentId") Long documentId,
                @Param("embeddingModelId") Long embeddingModelId,
                @Param("modelVersion") Integer modelVersion,
                @Param("indexVersion") Long indexVersion,
                @Param("maxAttempts") int maxAttempts);

    KnowledgeIndexTaskRecord claimNext();

    int markSuccess(@Param("taskId") Long taskId);

    int markFailedRetryable(@Param("taskId") Long taskId,
                            @Param("errorCode") String errorCode,
                            @Param("errorMessage") String errorMessage);

    int markFailedTerminal(@Param("taskId") Long taskId,
                           @Param("errorCode") String errorCode,
                           @Param("errorMessage") String errorMessage);

    int retry(@Param("taskId") Long taskId);

    int recoverStale(@Param("lockedBefore") LocalDateTime lockedBefore);

    long countNotSuccessful(@Param("knowledgeBaseId") Long knowledgeBaseId,
                            @Param("indexVersion") Long indexVersion);

    KnowledgeIndexTaskRecord findLatestFailedByDocumentId(@Param("documentId") Long documentId);

    KnowledgeIndexTaskRecord findById(@Param("taskId") Long taskId);
}
