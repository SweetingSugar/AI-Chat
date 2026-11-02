package com.example.aichat.boot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class KnowledgePostgresqlDataSourceConfiguration {

    @Bean(name = "knowledgePostgresqlDataSourceProperties")
    @ConfigurationProperties("knowledge.datasource.postgresql")
    DataSourceProperties knowledgePostgresqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "knowledgePostgresqlDataSource")
    @ConfigurationProperties("knowledge.datasource.postgresql.hikari")
    HikariDataSource knowledgePostgresqlDataSource(
            @Qualifier("knowledgePostgresqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
