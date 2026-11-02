package com.example.aichat.boot.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.example.aichat.knowledge.mapper.typehandler.PgVectorTypeHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = "com.example.aichat.knowledge.mapper",
        sqlSessionTemplateRef = "knowledgePostgresqlSqlSessionTemplate")
public class PostgresqlMybatisConfiguration {

    @Bean(name = "knowledgePostgresqlSqlSessionFactory")
    SqlSessionFactory knowledgePostgresqlSqlSessionFactory(
            @Qualifier("knowledgePostgresqlDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/postgresql/**/*.xml"));

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        configuration.getTypeHandlerRegistry().register(PgVectorTypeHandler.class);
        factory.setConfiguration(configuration);
        return factory.getObject();
    }

    @Bean(name = "knowledgePostgresqlSqlSessionTemplate")
    SqlSessionTemplate knowledgePostgresqlSqlSessionTemplate(
            @Qualifier("knowledgePostgresqlSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "knowledgePostgresqlTransactionManager")
    DataSourceTransactionManager knowledgePostgresqlTransactionManager(
            @Qualifier("knowledgePostgresqlDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
