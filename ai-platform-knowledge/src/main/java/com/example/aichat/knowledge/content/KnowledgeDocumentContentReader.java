package com.example.aichat.knowledge.content;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.file.content.FileContent;
import com.example.aichat.file.service.FileContentService;
import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.model.KnowledgeDocumentRecord;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.knowledge.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeDocumentContentReader {
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeBaseRepository baseRepository;
    private final FileContentService fileContentService;

    public KnowledgeDocumentContentReader(KnowledgeDocumentRepository documentRepository,
                                          KnowledgeBaseRepository baseRepository,
                                          FileContentService fileContentService) {
        this.documentRepository = documentRepository;
        this.baseRepository = baseRepository;
        this.fileContentService = fileContentService;
    }

    public String read(Long documentId) {
        KnowledgeDocumentRecord document = documentRepository.findById(documentId)
                .filter(record -> !record.deleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND));
        var base = baseRepository.findById(document.knowledgeBaseId())
                .filter(record -> !record.deleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND));
        if (document.sourceType() == KnowledgeDocumentSourceType.TEXT) {
            return requireContent(document.rawContent());
        }
        if (document.fileId() == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_PARSE_FAILED,
                    "Knowledge file reference is missing");
        }
        List<FileContent> contents = fileContentService.readOwnedActive(base.userId(), List.of(document.fileId()));
        if (contents.isEmpty()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_PARSE_FAILED,
                    "Knowledge file content is unavailable");
        }
        return requireContent(contents.get(0).text());
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_PARSE_FAILED,
                    "Knowledge document content is empty");
        }
        return content;
    }
}
