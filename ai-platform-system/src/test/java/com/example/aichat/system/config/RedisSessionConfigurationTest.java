package com.example.aichat.system.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class RedisSessionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisSessionDaoConfiguration.class)
            .withBean(LettuceConnectionFactory.class,
                    () -> new LettuceConnectionFactory(new RedisStandaloneConfiguration()));

    @Test
    void registersRedisBackedSaTokenDao() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SaTokenDao.class);
            assertThat(context.getBean(SaTokenDao.class)).isInstanceOf(SaTokenDaoForRedisTemplate.class);
        });
    }

    @Import(SaTokenDaoForRedisTemplate.class)
    static class RedisSessionDaoConfiguration {
    }
}
