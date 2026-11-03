package com.ticketshop.admin.common.infrastructure;

import java.sql.*;
import java.time.*;
import org.apache.ibatis.type.*;

/** DATETIME has no zone. Bind and read explicit UTC calendar fields, independent of JVM timezone. */
@MappedTypes(Instant.class)
@MappedJdbcTypes(value = JdbcType.TIMESTAMP, includeNullJdbcType = true)
public class UtcInstantTypeHandler extends BaseTypeHandler<Instant> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant value, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, LocalDateTime.ofInstant(value, ZoneOffset.UTC));
    }
    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    @Override public Instant getNullableResult(ResultSet rs, String name) throws SQLException {
        return instant(rs.getObject(name, LocalDateTime.class));
    }
    @Override public Instant getNullableResult(ResultSet rs, int index) throws SQLException {
        return instant(rs.getObject(index, LocalDateTime.class));
    }
    @Override public Instant getNullableResult(CallableStatement cs, int index) throws SQLException {
        return instant(cs.getObject(index, LocalDateTime.class));
    }
}
