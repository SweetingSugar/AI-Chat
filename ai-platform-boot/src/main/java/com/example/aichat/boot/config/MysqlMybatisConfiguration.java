package com.example.aichat.boot.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MysqlMybatisConfiguration {

    @Bean(name = "sqlSessionFactory")
    @Primary
    SqlSessionFactory sqlSessionFactory(
            @Qualifier("dataSource") DataSource dataSource,
            MybatisPlusProperties properties,
            ApplicationContext applicationContext) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setApplicationContext(applicationContext);
        factory.setMapperLocations(properties.resolveMapperLocations());

        MybatisConfiguration configuration = new MybatisConfiguration();
        if (properties.getConfiguration() != null) {
            properties.getConfiguration().applyTo(configuration);
        }
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(properties.getGlobalConfig());
        return factory.getObject();
    }

    @Bean(name = "transactionManager")
    @Primary
    DataSourceTransactionManager transactionManager(
            @Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "sqlSessionTemplate")
    @Primary
    SqlSessionTemplate sqlSessionTemplate(
            @Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
