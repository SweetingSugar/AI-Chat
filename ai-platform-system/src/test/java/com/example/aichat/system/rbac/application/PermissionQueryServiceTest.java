package com.example.aichat.system.rbac.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.system.rbac.infrastructure.persistence.PermissionMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PermissionQueryServiceTest {
    @Test
    void returnsDistinctPermissionsForUser() {
        PermissionMapper mapper = Mockito.mock(PermissionMapper.class);
        Mockito.when(mapper.selectPermissionCodesByUserId(9L))
                .thenReturn(List.of("system:user:read", "system:user:read", "admin:statistics:read"));

        assertThat(new PermissionQueryService(mapper).permissionsOf(9L))
                .containsExactlyInAnyOrder("system:user:read", "admin:statistics:read");
    }
}
