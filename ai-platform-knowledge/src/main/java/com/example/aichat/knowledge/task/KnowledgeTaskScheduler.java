package com.example.aichat.knowledge.task;

import com.example.aichat.knowledge.repository.KnowledgeIndexTaskRepository;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

public class KnowledgeTaskScheduler {
    private final KnowledgeIndexTaskRepository taskRepository;
    private final KnowledgeIndexWorker worker;
    private final long lockTimeoutMillis;

    public KnowledgeTaskScheduler(KnowledgeIndexTaskRepository taskRepository,
                                  KnowledgeIndexWorker worker,
                                  long lockTimeoutMillis) {
        this.taskRepository = taskRepository;
        this.worker = worker;
        this.lockTimeoutMillis = lockTimeoutMillis;
    }

    @Scheduled(fixedDelayString = "${knowledge.vector.task-poll-interval:2000}")
    public void poll() {
        taskRepository.recoverStale(LocalDateTime.now().minusNanos(lockTimeoutMillis * 1_000_000));
        worker.runOnce();
    }
}
