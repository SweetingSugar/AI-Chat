package com.example.aichat.boot.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "knowledge.postgresql",
        name = "migration-enabled",
        havingValue = "true")
public class KnowledgeDatabaseMigrationConfiguration {

    @Bean(name = "knowledgeFlyway")
    Flyway knowledgeFlyway(@Qualifier("knowledgePostgresqlDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/knowledge-postgresql")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean
    FlywayMigrationInitializer knowledgeFlywayInitializer(
            @Qualifier("knowledgeFlyway") Flyway flyway) {
        return new FlywayMigrationInitializer(flyway);
    }
}
