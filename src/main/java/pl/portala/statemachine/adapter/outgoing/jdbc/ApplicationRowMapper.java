package pl.portala.statemachine.adapter.outgoing.jdbc;

import pl.portala.statemachine.domain.Application;
import pl.portala.statemachine.domain.ApplicationReason;
import pl.portala.statemachine.domain.ApplicationState;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/** Maps a row of the append-only {@code application} table back to the aggregate. */
final class ApplicationRowMapper {

    private ApplicationRowMapper() {
    }

    /** Used as a {@code RowMapper<Application>} method reference by the JdbcClient. */
    static Application map(ResultSet rs, int rowNum) throws SQLException {
        return Application.restore(
                rs.getLong("id"),
                UUID.fromString(rs.getString("obj_id")),
                toLocalDateTime(rs.getTimestamp("created_date")),
                rs.getLong("version"),
                ApplicationState.valueOf(rs.getString("state")),
                rs.getString("name"),
                rs.getString("body"),
                rs.getString("author"),
                toReason(rs.getString("reason")),
                readNullableLong(rs, "publication_id"));
    }

    static String toReasonColumn(Application application) {
        return application.reason() == null ? null : application.reason().name();
    }

    private static ApplicationReason toReason(String value) {
        return value == null || value.isBlank() ? null : ApplicationReason.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Long readNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
