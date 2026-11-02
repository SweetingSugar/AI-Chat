package com.example.aichat.knowledge.retrieval;

import java.util.List;

public record KnowledgeContext(List<KnowledgeSource> sources, String prompt) {
    public KnowledgeContext {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
