package com.example.aichat.admin.mapper;

import com.example.aichat.admin.dto.response.DailyStatisticsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminStatisticsMapper {
    Map<String, Object> selectUserOverview();

    Long countNewUsers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Long countActiveUsers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Map<String, Object> selectSessionOverview();

    Long countNewSessions(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Map<String, Object> selectMessageOverview(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Map<String, Object> selectGenerationOverview(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Map<String, Object> selectAiOverview(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<DailyStatisticsRow> selectNewUserTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<DailyStatisticsRow> selectActiveUserTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<DailyStatisticsRow> selectSessionTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<DailyStatisticsRow> selectMessageTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<DailyStatisticsRow> selectGenerationTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<DailyStatisticsRow> selectAiTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<Map<String, Object>> selectTokenByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                @Param("offset") int offset, @Param("size") int size);

    List<Map<String, Object>> selectTokenByModel(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                  @Param("offset") int offset, @Param("size") int size);

    List<Map<String, Object>> selectTokenByProvider(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                    @Param("offset") int offset, @Param("size") int size);

    List<Map<String, Object>> selectTokenByUser(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                @Param("offset") int offset, @Param("size") int size);
}
