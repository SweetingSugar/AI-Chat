package com.example.aichat.boot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class MysqlDataSourceConfiguration {

    @Bean(name = "mysqlDataSourceProperties")
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "mysqlDataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource mysqlDataSource(
            @Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
