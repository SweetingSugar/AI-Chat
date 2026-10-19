package com.example.aichat.admin.dto.request;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class StatisticsQuery {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    public StatisticsQuery() {
    }

    public StatisticsQuery(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
    }

    public void validate() {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "from and to are required");
        }
        if (!to.isAfter(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "to must be after from");
        }
        if (ChronoUnit.DAYS.between(from, to) > 365) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Statistics range cannot exceed 365 days");
        }
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public LocalDateTime getFromDateTime() {
        return from == null ? null : from.atStartOfDay();
    }

    public LocalDateTime getToDateTime() {
        return to == null ? null : to.atStartOfDay();
    }
}
