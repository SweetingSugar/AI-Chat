package com.example.aichat.common.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CurrentUserProviderContractTest {
    @Test
    void requiresAuthenticationWhenNoCurrentUserExists() {
        CurrentUserProvider provider = Optional::empty;
        assertThatThrownBy(provider::requireCurrentUser)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }
}
