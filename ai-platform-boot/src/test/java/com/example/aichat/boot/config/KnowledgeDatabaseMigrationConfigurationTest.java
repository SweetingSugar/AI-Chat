package com.example.aichat.boot.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDatabaseMigrationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(MysqlDataSourceConfiguration.class,
                    KnowledgePostgresqlDataSourceConfiguration.class,
                    KnowledgeDatabaseMigrationConfiguration.class)
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
                    "knowledge.datasource.postgresql.hikari.initialization-fail-timeout=-1",
                    "knowledge.postgresql.migration-enabled=false");

    @Test
    void doesNotCreateKnowledgeFlywayWhenMigrationIsDisabled() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean("knowledgeFlyway"));
    }

    @Test
    void bindsKnowledgeFlywayToKnowledgePostgresqlDataSource() {
        contextRunner.run(context -> {
            DataSource dataSource = context.getBean("knowledgePostgresqlDataSource", DataSource.class);
            Flyway flyway = new KnowledgeDatabaseMigrationConfiguration().knowledgeFlyway(dataSource);

            assertThat(flyway.getConfiguration().getDataSource()).isSameAs(dataSource);
        });
    }
}
