package com.example.aichat.boot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(MysqlDataSourceConfiguration.class,
                    KnowledgePostgresqlDataSourceConfiguration.class)
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
    void keepsMySqlAsPrimaryDataSource() {
        contextRunner.run(context -> {
            DataSource primaryDataSource = context.getBean(DataSource.class);
            HikariDataSource mysqlDataSource = context.getBean(
                    "mysqlDataSource", HikariDataSource.class);
            HikariDataSource knowledgePostgresqlDataSource = context.getBean(
                    "knowledgePostgresqlDataSource", HikariDataSource.class);

            assertThat(primaryDataSource).isSameAs(mysqlDataSource);
            assertThat(mysqlDataSource.getJdbcUrl())
                    .isEqualTo("jdbc:mysql://localhost:3306/ai_chat");
            assertThat(knowledgePostgresqlDataSource.getJdbcUrl())
                    .isEqualTo("jdbc:postgresql://localhost:5432/ai_chat");
            assertThat(knowledgePostgresqlDataSource.getInitializationFailTimeout())
                    .isEqualTo(-1L);
            assertThat(context.getBeansOfType(DataSource.class)).hasSize(2);
        });
    }

    @Test
    void createsKnowledgePostgresqlDataSourceWithoutDefaultJdbcTemplate() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("postgresqlJdbcTemplate");
        });
    }
}
