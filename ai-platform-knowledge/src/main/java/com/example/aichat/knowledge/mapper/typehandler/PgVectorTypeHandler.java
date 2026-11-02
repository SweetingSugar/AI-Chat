package com.example.aichat.knowledge.mapper.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PgVectorTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index,
                                    float[] parameter, JdbcType jdbcType) throws SQLException {
        statement.setString(index, serialize(parameter));
    }

    @Override
    public float[] getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public float[] getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public float[] getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private String serialize(float[] vector) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(Float.toString(vector[index]));
        }
        return result.append(']').toString();
    }

    private float[] parse(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
            throw new SQLException("Invalid PostgreSQL vector value");
        }
        String body = normalized.substring(1, normalized.length() - 1).trim();
        if (body.isEmpty()) {
            return new float[0];
        }
        String[] values = body.split(",");
        float[] result = new float[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = Float.parseFloat(values[index].trim());
        }
        return result;
    }
}
