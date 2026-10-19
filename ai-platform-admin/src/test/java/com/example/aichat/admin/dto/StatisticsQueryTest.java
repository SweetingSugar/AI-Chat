package com.example.aichat.admin.dto;

import com.example.aichat.admin.dto.request.StatisticsQuery;
import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatisticsQueryTest {

    @Test
    void acceptsARangeUpTo366Days() {
        StatisticsQuery query = new StatisticsQuery(
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0));

        assertDoesNotThrow(query::validate);
    }

    @Test
    void rejectsReversedRange() {
        StatisticsQuery query = new StatisticsQuery(
                LocalDateTime.of(2026, 1, 2, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThrows(BusinessException.class, query::validate);
    }

    @Test
    void rejectsRangeLongerThan366Days() {
        StatisticsQuery query = new StatisticsQuery(
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 3, 0, 0));

        assertThrows(BusinessException.class, query::validate);
    }
}
