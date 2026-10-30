package com.example.aichat.boot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(DataSourceConfiguration.class)
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
                    "app.datasource.postgresql.hikari.initialization-fail-timeout=-1");

    @Test
    void keepsMySqlAsPrimaryDataSource() {
        contextRunner.run(context -> {
            DataSource primaryDataSource = context.getBean(DataSource.class);
            DataSource mysqlDataSource = context.getBean("dataSource", DataSource.class);
            HikariDataSource hikariDataSource = (HikariDataSource) mysqlDataSource;

            assertThat(primaryDataSource).isSameAs(mysqlDataSource);
            assertThat(hikariDataSource.getJdbcUrl()).isEqualTo("jdbc:mysql://localhost:3306/ai_chat");
        });
    }

    @Test
    void createsNamedPostgresqlDataSourceAndJdbcTemplate() {
        contextRunner.run(context -> {
            HikariDataSource postgresqlDataSource = context.getBean("postgresqlDataSource", HikariDataSource.class);
            JdbcTemplate postgresqlJdbcTemplate = context.getBean("postgresqlJdbcTemplate", JdbcTemplate.class);

            assertThat(postgresqlDataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/ai_chat");
            assertThat(postgresqlDataSource.getInitializationFailTimeout()).isEqualTo(-1L);
            assertThat(postgresqlJdbcTemplate.getDataSource()).isSameAs(postgresqlDataSource);
            assertThat(context.getBeansOfType(DataSource.class)).hasSize(2);
        });
    }
}
