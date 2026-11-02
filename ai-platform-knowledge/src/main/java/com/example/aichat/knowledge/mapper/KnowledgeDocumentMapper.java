package com.example.aichat.knowledge.mapper;

import com.example.aichat.knowledge.model.KnowledgeDocumentRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KnowledgeDocumentMapper {
    List<KnowledgeDocumentRecord> findPageByKnowledgeBaseId(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    List<KnowledgeDocumentRecord> findByKnowledgeBaseId(
            @Param("knowledgeBaseId") Long knowledgeBaseId);

    KnowledgeDocumentRecord findById(@Param("id") Long id);

    KnowledgeDocumentRecord findActiveByKnowledgeBaseAndFile(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("fileId") Long fileId);

    Long insert(@Param("knowledgeBaseId") Long knowledgeBaseId,
                @Param("sourceType") String sourceType,
                @Param("fileId") Long fileId,
                @Param("title") String title,
                @Param("rawContent") String rawContent,
                @Param("indexVersion") Long indexVersion);

    int markIndexing(@Param("documentId") Long documentId,
                     @Param("indexVersion") Long indexVersion);

    int markReady(@Param("documentId") Long documentId,
                  @Param("indexVersion") Long indexVersion,
                  @Param("chunkCount") int chunkCount);

    int updateContentHash(@Param("documentId") Long documentId,
                          @Param("contentHash") String contentHash);

    int markFailed(@Param("documentId") Long documentId,
                   @Param("status") String status,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);

    int markDeleted(@Param("id") Long id);
}
