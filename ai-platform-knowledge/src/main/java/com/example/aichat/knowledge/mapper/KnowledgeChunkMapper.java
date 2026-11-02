package com.example.aichat.knowledge.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KnowledgeChunkMapper {
    List<KnowledgeChunkRow> findByDocumentId(@Param("documentId") Long documentId);

    List<KnowledgeChunkRow> findByDocumentIdAndIndexVersion(
            @Param("documentId") Long documentId,
            @Param("indexVersion") Long indexVersion);

    Long insert(@Param("documentId") Long documentId,
                @Param("indexVersion") Long indexVersion,
                @Param("chunkIndex") Integer chunkIndex,
                @Param("content") String content,
                @Param("estimatedTokens") Integer estimatedTokens,
                @Param("metadataJson") String metadataJson);

    int deleteByDocumentId(@Param("documentId") Long documentId);

    int deleteByDocumentAndIndexVersion(@Param("documentId") Long documentId,
                                        @Param("indexVersion") Long indexVersion);
}
