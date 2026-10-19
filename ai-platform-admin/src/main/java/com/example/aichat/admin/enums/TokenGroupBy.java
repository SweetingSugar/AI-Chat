package com.example.aichat.admin.enums;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;

import java.util.Locale;

public enum TokenGroupBy {
    DAY,
    MODEL,
    PROVIDER,
    USER;

    public static TokenGroupBy from(String value) {
        if (value == null || value.isBlank()) return DAY;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "groupBy must be DAY, MODEL, PROVIDER, or USER");
        }
    }
}
