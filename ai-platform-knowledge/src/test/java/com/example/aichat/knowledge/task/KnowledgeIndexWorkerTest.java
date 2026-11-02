package com.example.aichat.knowledge.task;

import com.example.aichat.knowledge.model.KnowledgeIndexTaskRecord;
import com.example.aichat.knowledge.repository.KnowledgeIndexTaskRepository;
import com.example.aichat.knowledge.service.KnowledgeIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIndexWorkerTest {
    @Mock
    private KnowledgeIndexTaskRepository taskRepository;
    @Mock
    private KnowledgeIndexService indexService;

    @Test
    void claimsAndIndexesOneTask() {
        KnowledgeIndexTaskRecord task = org.mockito.Mockito.mock(KnowledgeIndexTaskRecord.class);
        when(taskRepository.claimNext()).thenReturn(Optional.of(task));
        KnowledgeIndexWorker worker = new KnowledgeIndexWorker(taskRepository, indexService);

        worker.runOnce();

        verify(indexService).index(task);
    }
}
