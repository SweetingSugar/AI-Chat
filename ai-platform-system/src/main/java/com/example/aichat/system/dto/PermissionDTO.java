package com.example.aichat.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PermissionDTO(Long parentId,
                            @NotBlank @Size(max = 100) String permissionName,
                            @Size(max = 100) String permissionCode,
                            @NotNull Integer permissionType,
                            @Size(max = 255) String path,
                            @Size(max = 255) String component,
                            @Size(max = 100) String icon,
                            Integer sort,
                            Integer status) { }
