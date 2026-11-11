package com.example.aichat.tool.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.aichat.tool.validation.TemplateValueResolver;
import com.example.aichat.tool.validation.ToolConfigCodec;
import com.example.aichat.tool.validation.UrlSafetyValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ToolConfigurationTest {
    @Test
    void exposesToolPropertiesAndSchemaValidator() {
        new ApplicationContextRunner()
                .withUserConfiguration(ToolConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("tool.max-header-count=32")
                .run(context -> {
                    assertThat(context).hasSingleBean(ToolProperties.class);
                    assertThat(context.getBean(ToolProperties.class).getMaxHeaderCount()).isEqualTo(32);
                    assertThat(context).hasSingleBean(JsonSchemaValidator.class);
                    assertThat(context).hasSingleBean(TemplateValueResolver.class);
                    assertThat(context).hasSingleBean(ToolConfigCodec.class);
                    assertThat(context).hasSingleBean(UrlSafetyValidator.class);
                });
    }

    @Test
    void usesSafeDefaults() {
        new ApplicationContextRunner()
                .withUserConfiguration(ToolConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    ToolProperties properties = context.getBean(ToolProperties.class);
                    assertThat(properties.getMaxHeaderCount()).isEqualTo(32);
                    assertThat(properties.getMaxBodyBytes()).isEqualTo(1_048_576);
                    assertThat(properties.getMaxResponseBytes()).isEqualTo(1_048_576);
                    assertThat(properties.getDefaultConnectTimeout()).isEqualTo(10);
                    assertThat(properties.getDefaultReadTimeout()).isEqualTo(60);
                    assertThat(properties.getMaxTimeout()).isEqualTo(600);
                    assertThat(properties.isAllowPrivateNetwork()).isFalse();
                });
    }
}
