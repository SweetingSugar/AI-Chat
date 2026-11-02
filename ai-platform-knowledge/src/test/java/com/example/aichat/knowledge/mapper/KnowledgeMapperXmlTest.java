package com.example.aichat.knowledge.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.model.KnowledgeChunkEmbeddingRecord;
import com.example.aichat.knowledge.model.KnowledgeChunkSearchResult;
import com.example.aichat.knowledge.model.KnowledgeIndexTaskRecord;
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
class KnowledgeMapperXmlTest {
    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private KnowledgeBaseMapper baseMapper;
    private KnowledgeDocumentMapper documentMapper;
    private KnowledgeChunkMapper chunkMapper;
    private KnowledgeChunkEmbeddingMapper embeddingMapper;
    private KnowledgeIndexTaskMapper taskMapper;

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

        baseMapper = template.getMapper(KnowledgeBaseMapper.class);
        documentMapper = template.getMapper(KnowledgeDocumentMapper.class);
        chunkMapper = template.getMapper(KnowledgeChunkMapper.class);
        embeddingMapper = template.getMapper(KnowledgeChunkEmbeddingMapper.class);
        taskMapper = template.getMapper(KnowledgeIndexTaskMapper.class);
    }

    @Test
    void mapsGeneratedKeysAndJsonMetadata() {
        Long baseId = baseMapper.insert(200L, "Mapper 测试", null, 30L, 1);
        assertThat(baseMapper.findOwnedActive(200L, baseId).name()).isEqualTo("Mapper 测试");

        Long documentId = documentMapper.insert(baseId, KnowledgeDocumentSourceType.TEXT.name(),
                null, "文档", "内容", 1L);
        Long chunkId = chunkMapper.insert(documentId, 1L, 0, "内容", 2,
                "{\"section\":\"test\"}");

        assertThat(chunkMapper.findByDocumentId(documentId))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(chunkId);
                    assertThat(row.metadataJson()).isEqualTo("{\"section\":\"test\"}");
                });
    }

    @Test
    void bindsVectorsSearchesAndClaimsTask() {
        Long baseId = baseMapper.insert(201L, "Vector 测试", null, 31L, 1);
        Long documentId = documentMapper.insert(baseId, KnowledgeDocumentSourceType.TEXT.name(),
                null, "向量文档", "向量内容", 1L);
        Long chunkId = chunkMapper.insert(documentId, 1L, 0, "向量内容", 2,
                "{}");
        embeddingMapper.insertAll(List.of(new KnowledgeChunkEmbeddingRecord(
                null, chunkId, baseId, 31L, 1, 3, 1L, new float[]{1, 0, 0})));

        List<KnowledgeChunkSearchResult> results = embeddingMapper.search(
                baseId, 31L, 3, 1L, new float[]{1, 0, 0}, 1);

        assertThat(results).singleElement().extracting(KnowledgeChunkSearchResult::chunkId)
                .isEqualTo(chunkId);

        Long taskId = taskMapper.insert(baseId, documentId, 31L, 1, 1L, 3);
        KnowledgeIndexTaskRecord task = taskMapper.claimNext();
        assertThat(task).isNotNull();
        assertThat(task.id()).isEqualTo(taskId);
        assertThat(task.status().name()).isEqualTo("RUNNING");
        assertThat(taskMapper.claimNext()).isNull();
    }
}
