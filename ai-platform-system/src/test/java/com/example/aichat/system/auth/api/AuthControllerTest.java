package com.example.aichat.system.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.system.auth.application.AuthApplicationService;
import com.example.aichat.system.auth.application.LoginResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.Mockito;

class AuthControllerTest {
    @Test
    void loginEndpointReturnsSessionToken() throws Exception {
        AuthApplicationService auth = Mockito.mock(AuthApplicationService.class);
        CurrentUserProvider currentUser = Mockito.mock(CurrentUserProvider.class);
        when(auth.login(any())).thenReturn(new LoginResult(1L, "alice", "token-value"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, currentUser)).build();

        mvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.token").value("token-value"));
    }
}
