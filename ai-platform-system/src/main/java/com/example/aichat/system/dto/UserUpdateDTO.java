package com.example.aichat.system.dto;

import jakarta.validation.constraints.Size;

public record UserUpdateDTO(@Size(max = 50) String nickname,
                            @Size(max = 100) String email,
                            @Size(max = 20) String phone,
                            @Size(max = 500) String avatar) { }
