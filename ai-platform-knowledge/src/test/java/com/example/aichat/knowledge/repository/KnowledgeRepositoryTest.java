package com.example.aichat.knowledge.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.example.aichat.knowledge.mapper.KnowledgeBaseMapper;
import com.example.aichat.knowledge.mapper.KnowledgeChunkEmbeddingMapper;
import com.example.aichat.knowledge.mapper.KnowledgeChunkMapper;
import com.example.aichat.knowledge.mapper.KnowledgeDocumentMapper;
import com.example.aichat.knowledge.mapper.KnowledgeIndexTaskMapper;
import com.example.aichat.knowledge.model.KnowledgeChunkEmbeddingRecord;
import com.example.aichat.knowledge.model.KnowledgeChunkSearchResult;
import com.example.aichat.knowledge.model.KnowledgeChunkRecord;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KnowledgeRepositoryTest {
    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private KnowledgeBaseRepository baseRepository;
    private KnowledgeDocumentRepository documentRepository;
    private KnowledgeChunkRepository chunkRepository;
    private KnowledgeChunkEmbeddingRepository embeddingRepository;
    private KnowledgeIndexTaskRepository taskRepository;

    @BeforeAll
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/knowledge-postgresql/V1__knowledge_schema.sql"))
                .execute(dataSource);

        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/postgresql/**/*.xml"));
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        factory.setConfiguration(configuration);
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(sqlSessionFactory);

        baseRepository = new KnowledgeBaseRepository(template.getMapper(KnowledgeBaseMapper.class));
        documentRepository = new KnowledgeDocumentRepository(template.getMapper(KnowledgeDocumentMapper.class));
        chunkRepository = new KnowledgeChunkRepository(
                template.getMapper(KnowledgeChunkMapper.class), new com.fasterxml.jackson.databind.ObjectMapper());
        embeddingRepository = new KnowledgeChunkEmbeddingRepository(
                template.getMapper(KnowledgeChunkEmbeddingMapper.class));
        taskRepository = new KnowledgeIndexTaskRepository(template.getMapper(KnowledgeIndexTaskMapper.class));
    }

    @Test
    void storesAndSearchesVectorsByModelAndDimension() {
        Long baseId = baseRepository.insert(100L, "业务规范", null, 20L, 1);
        Long documentId = documentRepository.insert(baseId,
                com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType.TEXT,
                null, "报销", "报销标准", 1L);
        KnowledgeChunkRecord chunk = chunkRepository.insert(new KnowledgeChunkRecord(
                null, documentId, 0, "报销标准", 2, Map.of("section", "finance"), 1L));
        embeddingRepository.insertAll(List.of(
                new KnowledgeChunkEmbeddingRecord(null, chunk.id(), baseId, 20L, 1, 3, 1L,
                        new float[]{1, 0, 0})));

        List<KnowledgeChunkSearchResult> results = embeddingRepository.search(
                baseId, 20L, 3, 1L, new float[]{1, 0, 0}, 1);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.chunkId()).isEqualTo(chunk.id());
            assertThat(result.documentId()).isEqualTo(documentId);
            assertThat(result.title()).isEqualTo("报销");
            assertThat(result.content()).isEqualTo("报销标准");
            assertThat(result.distance()).isLessThan(0.01);
        });
        assertThat(embeddingRepository.search(baseId, 21L, 3, 1L,
                new float[]{1, 0, 0}, 1)).isEmpty();
    }

    @Test
    void claimsPendingTaskWithoutReclaimingIt() {
        Long baseId = baseRepository.insert(101L, "任务", null, 20L, 1);
        Long documentId = documentRepository.insert(baseId,
                com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType.TEXT,
                null, "文档", "内容", 1L);
        taskRepository.insert(baseId, documentId, 20L, 1, 1L, 3);

        assertThat(taskRepository.claimNext()).isPresent();
        assertThat(taskRepository.claimNext()).isEmpty();
    }
}
