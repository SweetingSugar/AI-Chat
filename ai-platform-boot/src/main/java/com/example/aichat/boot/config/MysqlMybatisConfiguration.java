package com.example.aichat.boot.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = {
                "com.example.aichat.system.mapper",
                "com.example.aichat.ai.mapper",
                "com.example.aichat.chat.mapper",
                "com.example.aichat.admin.mapper",
                "com.example.aichat.file.mapper"
        },
        sqlSessionTemplateRef = "mysqlSqlSessionTemplate")
public class MysqlMybatisConfiguration {

    @Bean(name = "mysqlSqlSessionFactory")
    @Primary
    SqlSessionFactory mysqlSqlSessionFactory(
            @Qualifier("mysqlDataSource") DataSource dataSource,
            MybatisPlusProperties properties,
            ApplicationContext applicationContext) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setApplicationContext(applicationContext);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/*.xml"));

        MybatisConfiguration configuration = new MybatisConfiguration();
        if (properties.getConfiguration() != null) {
            properties.getConfiguration().applyTo(configuration);
        }
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(properties.getGlobalConfig());
        return factory.getObject();
    }

    @Bean(name = "mysqlTransactionManager")
    @Primary
    DataSourceTransactionManager mysqlTransactionManager(
            @Qualifier("mysqlDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "mysqlSqlSessionTemplate")
    @Primary
    SqlSessionTemplate mysqlSqlSessionTemplate(
            @Qualifier("mysqlSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
