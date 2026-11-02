package com.example.aichat.knowledge.task;

import com.example.aichat.knowledge.repository.KnowledgeIndexTaskRepository;
import com.example.aichat.knowledge.service.KnowledgeIndexService;

public class KnowledgeIndexWorker {
    private final KnowledgeIndexTaskRepository taskRepository;
    private final KnowledgeIndexService indexService;

    public KnowledgeIndexWorker(KnowledgeIndexTaskRepository taskRepository,
                                KnowledgeIndexService indexService) {
        this.taskRepository = taskRepository;
        this.indexService = indexService;
    }

    public void runOnce() {
        taskRepository.claimNext().ifPresent(indexService::index);
    }
}
