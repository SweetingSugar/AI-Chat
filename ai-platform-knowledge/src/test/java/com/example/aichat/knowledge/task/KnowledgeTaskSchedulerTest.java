package com.example.aichat.knowledge.task;

import com.example.aichat.knowledge.repository.KnowledgeIndexTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KnowledgeTaskSchedulerTest {
    @Test
    void recoversStaleTasksBeforeClaimingNextTask() {
        KnowledgeIndexTaskRepository taskRepository = mock(KnowledgeIndexTaskRepository.class);
        KnowledgeIndexWorker worker = mock(KnowledgeIndexWorker.class);
        KnowledgeTaskScheduler scheduler = new KnowledgeTaskScheduler(taskRepository, worker, 600_000L);

        scheduler.poll();

        verify(taskRepository).recoverStale(any(LocalDateTime.class));
        verify(worker).runOnce();
    }
}
