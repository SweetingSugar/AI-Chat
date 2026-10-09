package com.example.aichat.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StatusUpdateDTO(@NotNull @Min(0) @Max(1) Integer status) { }
