package com.example.aichat.system.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CorsConfigurationTest {

    @Test
    void allowsPreflightRequestsFromAnyOriginWithCustomAuthenticationHeader() {
        ExposedCorsRegistry registry = new ExposedCorsRegistry();

        new com.example.aichat.system.config.CorsConfiguration().addCorsMappings(registry);

        CorsConfiguration configuration = registry.configurations().get("/**");
        assertEquals("https://frontend.example.com", configuration.checkOrigin("https://frontend.example.com"));
        assertEquals(List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE,
                HttpMethod.OPTIONS), configuration.checkHttpMethod(HttpMethod.POST));
        assertEquals(List.of("satoken", "content-type"), configuration.checkHeaders(List.of("satoken", "content-type")));
        assertFalse(configuration.getAllowCredentials());
    }

    private static class ExposedCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
