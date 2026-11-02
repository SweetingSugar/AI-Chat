package com.example.aichat.knowledge.task;

import com.example.aichat.knowledge.repository.KnowledgeIndexTaskRepository;
import com.example.aichat.knowledge.service.KnowledgeIndexService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "knowledge.vector", name = "enabled", havingValue = "true")
public class KnowledgeTaskConfiguration {

    @Bean
    KnowledgeIndexWorker knowledgeIndexWorker(KnowledgeIndexTaskRepository taskRepository,
                                              KnowledgeIndexService indexService) {
        return new KnowledgeIndexWorker(taskRepository, indexService);
    }

    @Bean
    KnowledgeTaskScheduler knowledgeTaskScheduler(KnowledgeIndexTaskRepository taskRepository,
                                                   KnowledgeIndexWorker worker,
                                                   @Value("${knowledge.vector.task-lock-timeout:600000}")
                                                   long lockTimeoutMillis) {
        return new KnowledgeTaskScheduler(taskRepository, worker, lockTimeoutMillis);
    }
}
