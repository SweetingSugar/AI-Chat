package com.example.aichat.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.system.dto.IdSetDTO;
import com.example.aichat.system.dto.StatusUpdateDTO;
import com.example.aichat.system.dto.UserUpdateDTO;
import com.example.aichat.system.service.UserService;
import com.example.aichat.system.vo.UserVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/users")
public class UserController {
    private final UserService userService;
    public UserController(UserService userService) { this.userService = userService; }

    @GetMapping
    @SaCheckPermission("system:user:read")
    public ApiResponse<List<UserVO>> list() { return ApiResponse.success(userService.list().stream().map(UserVO::from).toList()); }

    @GetMapping("/{id}")
    @SaCheckPermission("system:user:read")
    public ApiResponse<UserVO> get(@PathVariable Long id) { return ApiResponse.success(UserVO.from(userService.getRequired(id))); }

    @PutMapping("/{id}")
    @SaCheckPermission("system:user:update")
    public ApiResponse<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO request) {
        return ApiResponse.success(UserVO.from(userService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @SaCheckPermission("system:user:update")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO request) {
        userService.updateStatus(id, request.status());
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/roles")
    @SaCheckPermission("system:user:assign-role")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody IdSetDTO request) {
        userService.assignRoles(id, request.ids());
        return ApiResponse.success(null);
    }
}
