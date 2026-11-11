package com.example.aichat.boot.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.example.aichat.knowledge.mapper.KnowledgeBaseMapper;
import com.example.aichat.system.mapper.UserMapper;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresqlMybatisConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    MybatisPlusAutoConfiguration.class))
            .withUserConfiguration(MysqlDataSourceConfiguration.class,
                    KnowledgePostgresqlDataSourceConfiguration.class,
                    MysqlMybatisConfiguration.class,
                    PostgresqlMybatisConfiguration.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:mysql://localhost:3306/ai_chat",
                    "spring.datasource.username=root",
                    "spring.datasource.password=test",
                    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "spring.datasource.hikari.initialization-fail-timeout=-1",
                    "knowledge.datasource.postgresql.url=jdbc:postgresql://localhost:5432/ai_chat",
                    "knowledge.datasource.postgresql.username=postgres",
                    "knowledge.datasource.postgresql.password=test",
                    "knowledge.datasource.postgresql.driver-class-name=org.postgresql.Driver",
                    "knowledge.datasource.postgresql.hikari.initialization-fail-timeout=-1");

    @Test
    void keepsDefaultMybatisSessionOnMySql() {
        contextRunner.run(context -> {
            SqlSessionFactory mysqlFactory = context.getBean(
                    "mysqlSqlSessionFactory", SqlSessionFactory.class);
            SqlSessionTemplate mysqlTemplate = context.getBean(
                    "mysqlSqlSessionTemplate", SqlSessionTemplate.class);

            assertThat(mysqlFactory.getConfiguration().getMappedStatementNames())
                    .anyMatch(name -> name.startsWith("com.example.aichat.system.mapper.UserMapper."));
            assertThat(mysqlFactory.getConfiguration().getMappedStatementNames())
                    .noneMatch(name -> name.startsWith("com.example.aichat.knowledge.mapper."));
            assertThat(mysqlFactory.getConfiguration().getEnvironment().getDataSource())
                    .isSameAs(context.getBean("mysqlDataSource"));
            assertThat(mysqlTemplate.getSqlSessionFactory()).isSameAs(mysqlFactory);
            assertThat(context.getBean("mysqlTransactionManager", DataSourceTransactionManager.class)
                    .getDataSource())
                    .isSameAs(context.getBean("mysqlDataSource"));
            assertThat(context.getBean(UserMapper.class)).isNotNull();
            assertThat(context.getBean(ToolDefinitionMapper.class)).isNotNull();
        });
    }

    @Test
    void createsIsolatedKnowledgePostgresqlMybatisSession() {
        contextRunner.run(context -> {
            SqlSessionFactory knowledgeFactory = context.getBean(
                    "knowledgePostgresqlSqlSessionFactory", SqlSessionFactory.class);
            SqlSessionTemplate knowledgeTemplate = context.getBean(
                    "knowledgePostgresqlSqlSessionTemplate", SqlSessionTemplate.class);

            assertThat(knowledgeFactory.getConfiguration().getMappedStatementNames())
                    .anyMatch(name -> name.startsWith("com.example.aichat.knowledge.mapper."));
            assertThat(knowledgeFactory.getConfiguration().getMappedStatementNames())
                    .noneMatch(name -> name.startsWith("com.example.aichat.system.mapper."));
            assertThat(knowledgeFactory.getConfiguration().getEnvironment().getDataSource())
                    .isSameAs(context.getBean("knowledgePostgresqlDataSource"));
            assertThat(knowledgeTemplate.getSqlSessionFactory()).isSameAs(knowledgeFactory);
            assertThat(context.getBean("knowledgePostgresqlTransactionManager",
                    DataSourceTransactionManager.class).getDataSource())
                    .isSameAs(context.getBean("knowledgePostgresqlDataSource"));
            assertThat(context.getBean(KnowledgeBaseMapper.class)).isNotNull();
        });
    }
}
