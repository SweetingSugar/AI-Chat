package com.example.aichat.admin.dto;

import com.example.aichat.admin.dto.request.StatisticsQuery;
import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatisticsQueryTest {

    @Test
    void acceptsDateRange() {
        StatisticsQuery query = new StatisticsQuery(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2));

        assertDoesNotThrow(query::validate);
    }

    @Test
    void rejectsReversedRange() {
        StatisticsQuery query = new StatisticsQuery(
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 1));

        assertThrows(BusinessException.class, query::validate);
    }

    @Test
    void rejectsRangeLongerThan365Days() {
        StatisticsQuery query = new StatisticsQuery(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 3));

        assertThrows(BusinessException.class, query::validate);
    }

    @Test
    void convertsDateBoundariesToStartOfDay() {
        StatisticsQuery query = new StatisticsQuery(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2));

        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), query.getFromDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 2, 0, 0), query.getToDateTime());
    }
}
