package com.example.aichat.admin.dto.request;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Duration;
import java.time.LocalDateTime;

public class StatisticsQuery {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;

    public StatisticsQuery() {
    }

    public StatisticsQuery(LocalDateTime from, LocalDateTime to) {
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
        if (Duration.between(from, to).toDays() > 366) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Statistics range cannot exceed 366 days");
        }
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public void setFrom(LocalDateTime from) {
        this.from = from;
    }

    public LocalDateTime getTo() {
        return to;
    }

    public void setTo(LocalDateTime to) {
        this.to = to;
    }
}
