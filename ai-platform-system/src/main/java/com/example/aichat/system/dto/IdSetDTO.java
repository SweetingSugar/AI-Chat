package com.example.aichat.system.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record IdSetDTO(@NotNull Set<Long> ids) { }
