package com.example.aichat.knowledge.mapper.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorTypeHandlerTest {

    @Test
    void bindsFloatArrayAsPostgresqlVectorLiteral() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        new PgVectorTypeHandler().setNonNullParameter(
                statement, 1, new float[]{1.0f, -2.5f}, JdbcType.VARCHAR);

        verify(statement).setString(1, "[1.0,-2.5]");
    }

    @Test
    void readsVectorLiteral() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("embedding")).thenReturn("[1.0,-2.5]");

        float[] result = new PgVectorTypeHandler().getNullableResult(resultSet, "embedding");

        assertThat(result).containsExactly(1.0f, -2.5f);
    }

    @Test
    void returnsNullForNullColumn() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("embedding")).thenReturn(null);

        assertThat(new PgVectorTypeHandler().getNullableResult(resultSet, "embedding"))
                .isNull();
    }
}
