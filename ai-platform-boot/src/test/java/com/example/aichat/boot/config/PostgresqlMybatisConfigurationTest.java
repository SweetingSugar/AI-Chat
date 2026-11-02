package com.example.aichat.boot.config;

import com.example.aichat.knowledge.mapper.KnowledgeBaseMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresqlMybatisConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    MybatisPlusAutoConfiguration.class))
            .withUserConfiguration(DataSourceConfiguration.class,
                    MysqlMybatisConfiguration.class,
                    PostgresqlMybatisConfiguration.class)
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
    void keepsDefaultMybatisSessionOnMySql() {
        contextRunner
                .run(context -> {
                    SqlSessionFactory factory = context.getBean("sqlSessionFactory", SqlSessionFactory.class);

                    assertThat(factory.getConfiguration().getEnvironment().getDataSource())
                            .isSameAs(context.getBean("dataSource"));
                    assertThat(context.getBean("transactionManager", DataSourceTransactionManager.class)
                            .getDataSource())
                            .isSameAs(context.getBean("dataSource"));
                });
    }

    @Test
    void createsIsolatedPostgresqlMybatisSession() {
        contextRunner.run(context -> {
            SqlSessionFactory factory = context.getBean(
                    "postgresqlSqlSessionFactory", SqlSessionFactory.class);
            SqlSessionTemplate template = context.getBean(
                    "postgresqlSqlSessionTemplate", SqlSessionTemplate.class);

            assertThat(factory.getConfiguration().getMappedStatementNames())
                    .anyMatch(name -> name.startsWith(
                            "com.example.aichat.knowledge.mapper.KnowledgeBaseMapper."));
            assertThat(factory.getConfiguration().getEnvironment().getDataSource())
                    .isSameAs(context.getBean("postgresqlDataSource"));
            assertThat(template.getSqlSessionFactory()).isSameAs(factory);
            assertThat(context.getBean("postgresqlTransactionManager"))
                    .isInstanceOf(DataSourceTransactionManager.class);
            assertThat(context.getBean(KnowledgeBaseMapper.class)).isNotNull();
        });
    }
}
