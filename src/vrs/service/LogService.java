package vrs.service;

import java.sql.SQLException;
import java.util.*;
import vrs.db.*;
import vrs.model.Models.LogEntry;

/** Reads the QUERY_LOG table for the employee "Query Log" screen. */
public final class LogService {
    private LogService() {}

    public record Stats(long total, long errors, double avgMs, long today) {}

    /**
     * @param type   ALL, SELECT, INSERT, UPDATE, DELETE or TCL
     * @param search text to find in the SQL or the user e-mail (may be empty)
     */
    public static List<LogEntry> recent(String type, boolean errorsOnly, String search, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT LogID, ExecutedAt, UserRole, UserEmail, QueryType, SqlText, RowsAffected, DurationMs, Status, ErrorMessage "
              + "FROM QUERY_LOG WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (type != null && !type.equals("ALL")) { sql.append(" AND QueryType = ?"); params.add(type); }
        if (errorsOnly) sql.append(" AND Status = 'ERROR'");
        if (search != null && !search.isBlank()) {
            sql.append(" AND (SqlText LIKE ? OR UserEmail LIKE ?)");
            params.add("%" + search.trim() + "%");
            params.add("%" + search.trim() + "%");
        }
        sql.append(" ORDER BY LogID DESC LIMIT ?");
        params.add(limit);
        List<LogEntry> out = new ArrayList<>();
        for (Row r : Db.query(sql.toString(), params.toArray()))
            out.add(new LogEntry(r.l("LogID"), r.dateTime("ExecutedAt"), r.str("UserRole"), r.str("UserEmail"),
                    r.str("QueryType"), r.str("SqlText"), r.intOrNull("RowsAffected"), r.intOrNull("DurationMs"),
                    r.str("Status"), r.str("ErrorMessage")));
        return out;
    }

    public static Stats stats() throws SQLException {
        Row r = Db.one("SELECT COUNT(*) AS Total, COALESCE(SUM(Status = 'ERROR'), 0) AS Errors, "
                + "COALESCE(AVG(DurationMs), 0) AS AvgMs, COALESCE(SUM(ExecutedAt >= CURDATE()), 0) AS Today FROM QUERY_LOG");
        return new Stats(r.l("Total"), r.l("Errors"), r.dec("AvgMs").doubleValue(), r.l("Today"));
    }

    public static void clear() throws SQLException {
        Db.update("DELETE FROM QUERY_LOG WHERE LogID > 0");
    }
}
