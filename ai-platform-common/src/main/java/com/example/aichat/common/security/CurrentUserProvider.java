package com.example.aichat.common.security;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import java.util.Optional;

public interface CurrentUserProvider {
    Optional<CurrentUser> findCurrentUser();
    default CurrentUser requireCurrentUser() {
        return findCurrentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }
}
