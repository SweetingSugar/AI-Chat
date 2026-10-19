package com.example.aichat.admin.dto.response;

import java.time.LocalDate;

public record UserTrendDTO(LocalDate date, long newUsers, long activeUsers) { }
