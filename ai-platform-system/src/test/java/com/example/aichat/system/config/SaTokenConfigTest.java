package com.example.aichat.system.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.servlet.util.SaTokenContextJakartaServletUtil;
import cn.dev33.satoken.spring.SaTokenContextRegister;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SaTokenConfigTest {

    @Test
    void returnsOnlyTheJsonErrorBodyForAnUnauthenticatedApiRequest() throws Exception {
        new SaTokenContextRegister();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setRequestURI("/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SaServletFilter filter = new SaTokenConfig().saServletFilter(new ObjectMapper());

        SaTokenContextJakartaServletUtil.setContext(request, response);
        try {
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> fail("Unauthenticated request must not reach the handler"));
        } finally {
            SaTokenContextJakartaServletUtil.clearContext();
        }

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":false,\"code\":\"UNAUTHENTICATED\",\"message\":\"Authentication is required\",\"data\":null}");
    }
}
