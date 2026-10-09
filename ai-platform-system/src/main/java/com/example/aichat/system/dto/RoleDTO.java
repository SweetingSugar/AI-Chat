package com.example.aichat.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleDTO(@NotBlank @Size(max = 50) String roleName,
                      @NotBlank @Size(max = 50) String roleCode,
                      @Size(max = 255) String description,
                      Integer sort,
                      Integer status) { }
