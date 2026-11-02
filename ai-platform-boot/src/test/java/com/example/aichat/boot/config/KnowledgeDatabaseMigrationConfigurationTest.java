package com.example.aichat.boot.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDatabaseMigrationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(DataSourceConfiguration.class,
                    KnowledgeDatabaseMigrationConfiguration.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:mysql://localhost:3306/ai_chat",
                    "spring.datasource.username=root",
                    "spring.datasource.password=test",
                    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "spring.datasource.hikari.initialization-fail-timeout=-1",
                    "app.datasource.postgresql.url=jdbc:postgresql://localhost:5432/ai_chat",
                    "app.datasource.postgresql.username=postgres",
                    "app.datasource.postgresql.password=test",
                    "app.datasource.postgresql.driver-class-name=org.postgresql.Driver",
                    "app.datasource.postgresql.hikari.initialization-fail-timeout=-1",
                    "knowledge.vector.migration-enabled=false");

    @Test
    void doesNotCreateKnowledgeFlywayWhenMigrationIsDisabled() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean("knowledgeFlyway"));
    }
}
