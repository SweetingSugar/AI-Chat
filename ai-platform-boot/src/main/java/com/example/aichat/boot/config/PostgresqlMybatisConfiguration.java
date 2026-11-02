package com.example.aichat.boot.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
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
        sqlSessionTemplateRef = "postgresqlSqlSessionTemplate")
public class PostgresqlMybatisConfiguration {

    @Bean(name = "postgresqlSqlSessionFactory")
    SqlSessionFactory postgresqlSqlSessionFactory(
            @Qualifier("postgresqlDataSource") DataSource dataSource) throws Exception {
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

    @Bean(name = "postgresqlSqlSessionTemplate")
    SqlSessionTemplate postgresqlSqlSessionTemplate(
            @Qualifier("postgresqlSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "postgresqlTransactionManager")
    DataSourceTransactionManager postgresqlTransactionManager(
            @Qualifier("postgresqlDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
