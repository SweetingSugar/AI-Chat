package com.example.aichat.system.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.system.user.domain.User;
import com.example.aichat.system.user.domain.UserStatus;
import com.example.aichat.system.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthApplicationServiceTest {
    @Test
    void logsInAnActiveUserWithVerifiedPassword() {
        UserMapper users = Mockito.mock(UserMapper.class);
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("correct-password"));
        user.setStatus(UserStatus.ACTIVE);
        Mockito.when(users.findByUsername("alice")).thenReturn(user);
        InMemoryLoginSessionGateway session = new InMemoryLoginSessionGateway();

        LoginResult result = new AuthApplicationService(users, new BCryptPasswordEncoder(), session)
                .login(new LoginCommand("alice", "correct-password"));

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(session.loggedInUserId).isEqualTo(7L);
    }
}
