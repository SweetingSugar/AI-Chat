package com.example.aichat.boot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.example.aichat.common.config.MybatisPlusConfiguration;
import org.junit.jupiter.api.Test;

class MybatisPlusConfigurationTest {

    @Test
    void registersMySqlPaginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfiguration().mybatisPlusInterceptor();

        assertEquals(1, interceptor.getInterceptors().size());
        assertInstanceOf(PaginationInnerInterceptor.class, interceptor.getInterceptors().get(0));
    }
}
