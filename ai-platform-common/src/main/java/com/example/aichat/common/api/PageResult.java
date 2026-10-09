package com.example.aichat.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import java.util.function.Function;

/**
 * Standard payload for a paginated API response.
 *
 * @param records records in the current page
 * @param total total number of matching records
 * @param current one-based current page number
 * @param size requested page size
 * @param pages total number of pages
 */
public record PageResult<T>(List<T> records, long total, long current, long size, long pages) {

    public PageResult {
        records = List.copyOf(records);
    }

    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    public static <S, T> PageResult<T> from(IPage<S> page, Function<? super S, ? extends T> mapper) {
        List<T> records = page.getRecords().stream().<T>map(mapper::apply).toList();
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }
}
