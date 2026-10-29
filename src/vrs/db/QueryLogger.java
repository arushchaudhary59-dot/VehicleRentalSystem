package vrs.db;

import java.sql.*;
import vrs.service.Session;

/**
 * Writes one row into QUERY_LOG for every statement the app runs.
 * Uses its own connection with auto-commit, so the log entry is kept even
 * when the statement it describes is rolled back.
 */
final class QueryLogger {
    private static Connection logConn;

    private QueryLogger() {}

    static synchronized void reset() {
        try { if (logConn != null) logConn.close(); } catch (SQLException ignored) { }
        logConn = null;
    }

    static synchronized void log(String type, String sql, Integer rows, int ms, SQLException error) {
        try {
            if (logConn == null || logConn.isClosed()) {
                DbConfig c = Db.config();
                logConn = DriverManager.getConnection(c.url(), c.user, c.password);
            }
            try (PreparedStatement ps = logConn.prepareStatement(
                    "INSERT INTO QUERY_LOG (UserRole, UserEmail, QueryType, SqlText, RowsAffected, DurationMs, Status, ErrorMessage) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                Session.User u = Session.current();
                ps.setString(1, u == null ? "GUEST" : u.role().name());
                ps.setString(2, u == null ? null : u.email());
                ps.setString(3, type);
                ps.setString(4, sql);
                if (rows == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, rows);
                ps.setInt(6, ms);
                ps.setString(7, error == null ? "OK" : "ERROR");
                String msg = error == null ? null : error.getMessage();
                if (msg != null && msg.length() > 500) msg = msg.substring(0, 500);
                ps.setString(8, msg);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            // Logging must never break the real operation.
            System.err.println("[query log] " + e.getMessage());
            reset();
        }
    }
}
