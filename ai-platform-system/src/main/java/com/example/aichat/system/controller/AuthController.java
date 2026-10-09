package com.example.aichat.system.controller;

import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.system.dto.LoginDTO;
import com.example.aichat.system.dto.PasswordChangeDTO;
import com.example.aichat.system.dto.RegisterDTO;
import com.example.aichat.system.service.AuthService;
import com.example.aichat.system.service.UserService;
import com.example.aichat.system.vo.LoginVO;
import com.example.aichat.system.vo.SessionVO;
import com.example.aichat.system.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthService authService, UserService userService, CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.userService = userService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/register")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterDTO request) {
        return ApiResponse.success(UserVO.from(userService.register(request)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO request, HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.login(request, clientIp(servletRequest)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success(null);
    }

    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll() {
        authService.logoutAll();
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        return ApiResponse.success(authService.me());
    }

    @GetMapping("/status")
    public ApiResponse<SessionVO> status() {
        return ApiResponse.success(authService.session());
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody PasswordChangeDTO request) {
        userService.changePassword(currentUserProvider.requireCurrentUser().id(), request.oldPassword(), request.newPassword());
        return ApiResponse.success(null);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
