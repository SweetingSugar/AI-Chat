package com.example.aichat.tool.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolInvocationLog;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolInvocationStatus;
import com.example.aichat.tool.enums.ToolSource;
import com.example.aichat.tool.enums.ToolType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ToolMapperIntegrationTest {
    @Container
    private final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    private ToolDefinitionMapper toolMapper;
    private ToolMcpServerMapper serverMapper;
    private ToolInvocationLogMapper logMapper;

    @BeforeAll
    void setUp() throws Exception {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        factory.setConfiguration(configuration);
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(sqlSessionFactory);
        toolMapper = template.getMapper(ToolDefinitionMapper.class);
        serverMapper = template.getMapper(ToolMcpServerMapper.class);
        logMapper = template.getMapper(ToolInvocationLogMapper.class);
    }

    @Test
    void storesToolConfigurationsAndInvocationLogs() {
        ToolMcpServer server = new ToolMcpServer();
        server.setServerCode("orders");
        server.setServerName("Orders MCP");
        server.setEndpointUrl("https://mcp.example.com");
        server.setAuthType(ToolAuthType.NONE);
        server.setCredentialVersion(1);
        server.setConnectTimeout(10);
        server.setReadTimeout(60);
        server.setStatus(1);
        serverMapper.insert(server);

        ToolDefinition tool = new ToolDefinition();
        tool.setToolCode("orders_lookup");
        tool.setToolName("Orders lookup");
        tool.setToolType(ToolType.HTTP);
        tool.setSource(ToolSource.MANUAL);
        tool.setStatus(1);
        tool.setAvailability(ToolAvailability.AVAILABLE);
        tool.setHttpConfig("{\"url\":\"https://example.com\"}");
        tool.setAuthType(ToolAuthType.NONE);
        tool.setCredentialVersion(1);
        toolMapper.insert(tool);

        ToolInvocationLog log = new ToolInvocationLog();
        log.setRequestId("request-1");
        log.setToolId(tool.getId());
        log.setToolCode(tool.getToolCode());
        log.setUserId(7L);
        log.setToolType(ToolType.HTTP);
        log.setStatus(ToolInvocationStatus.SUCCESS);
        log.setStartTime(LocalDateTime.now());
        logMapper.insert(log);

        assertThat(serverMapper.selectById(server.getId()).getServerCode()).isEqualTo("orders");
        assertThat(toolMapper.selectById(tool.getId()).getHttpConfig()).contains("example.com");
        assertThat(logMapper.selectById(log.getId()).getRequestId()).isEqualTo("request-1");
    }

    @Test
    void logicDeletedRowsAreNotReturnedByBaseMapperQueries() {
        ToolDefinition tool = new ToolDefinition();
        tool.setToolCode("deleted_tool");
        tool.setToolName("Deleted");
        tool.setToolType(ToolType.HTTP);
        tool.setSource(ToolSource.MANUAL);
        tool.setStatus(0);
        tool.setAvailability(ToolAvailability.AVAILABLE);
        tool.setAuthType(ToolAuthType.NONE);
        tool.setCredentialVersion(1);
        toolMapper.insert(tool);
        toolMapper.deleteById(tool.getId());

        assertThat(toolMapper.selectById(tool.getId())).isNull();
    }

    @Test
    void duplicateCodesAreRejectedByUniqueIndexes() {
        ToolDefinition first = new ToolDefinition();
        first.setToolCode("duplicate_tool");
        first.setToolName("First");
        first.setToolType(ToolType.HTTP);
        first.setSource(ToolSource.MANUAL);
        first.setStatus(1);
        first.setAvailability(ToolAvailability.AVAILABLE);
        first.setAuthType(ToolAuthType.NONE);
        first.setCredentialVersion(1);
        toolMapper.insert(first);

        ToolDefinition duplicate = new ToolDefinition();
        duplicate.setToolCode("duplicate_tool");
        duplicate.setToolName("Second");
        duplicate.setToolType(ToolType.HTTP);
        duplicate.setSource(ToolSource.MANUAL);
        duplicate.setStatus(1);
        duplicate.setAvailability(ToolAvailability.AVAILABLE);
        duplicate.setAuthType(ToolAuthType.NONE);
        duplicate.setCredentialVersion(1);

        assertThatThrownBy(() -> toolMapper.insert(duplicate))
                .hasMessageContaining("Duplicate");
    }
}
