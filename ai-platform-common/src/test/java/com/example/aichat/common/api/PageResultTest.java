package com.example.aichat.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void createsStandardPayloadFromMybatisPlusPage() {
        Page<String> page = new Page<>(2, 10, 25);
        page.setRecords(List.of("first", "second"));

        PageResult<String> result = PageResult.from(page);

        assertThat(result.records()).containsExactly("first", "second");
        assertThat(result.total()).isEqualTo(25);
        assertThat(result.current()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.pages()).isEqualTo(3);
    }

    @Test
    void mapsRecordsForApiViewObjects() {
        Page<Integer> page = new Page<>(1, 20, 2);
        page.setRecords(List.of(1, 2));

        PageResult<String> result = PageResult.from(page, value -> "item-" + value);

        assertThat(result.records()).containsExactly("item-1", "item-2");
    }
}
