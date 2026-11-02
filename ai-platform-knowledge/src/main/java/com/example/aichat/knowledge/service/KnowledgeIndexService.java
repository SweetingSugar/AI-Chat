package com.example.aichat.knowledge.service;

import com.example.aichat.knowledge.model.KnowledgeIndexTaskRecord;

public interface KnowledgeIndexService {
    void index(KnowledgeIndexTaskRecord task);
}
