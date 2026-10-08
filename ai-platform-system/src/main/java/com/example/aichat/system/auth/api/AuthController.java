package com.example.aichat.system.auth.api;

import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.system.auth.application.AuthApplicationService;
import com.example.aichat.system.auth.application.LoginCommand;
import com.example.aichat.system.auth.application.LoginResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthApplicationService authApplicationService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthApplicationService authApplicationService, CurrentUserProvider currentUserProvider) {
        this.authApplicationService = authApplicationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authApplicationService.login(new LoginCommand(request.username(), request.password())));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authApplicationService.logout();
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me() {
        return ApiResponse.success(currentUserProvider.requireCurrentUser());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
}
