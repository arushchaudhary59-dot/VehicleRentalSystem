package vrs.db;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Every SQL statement in the application goes through this class.
 *  - uses PreparedStatement with ? parameters (no SQL injection)
 *  - writes each statement, its duration and result to QUERY_LOG
 *  - offers transaction() for multi-statement operations
 *
 * The app is a single-user desktop program, so one shared connection is
 * enough. All calls run on one background thread (see vrs.ui.Async).
 */
public final class Db {
    private static DbConfig config = DbConfig.load();
    private static Connection conn;
    private static boolean inTransaction;

    private Db() {}

    /** Wraps a parameter (e.g. a password hash) so it is hidden in the query log. */
    public record Secret(Object value) {}
    public static Secret secret(Object value) { return new Secret(value); }

    @FunctionalInterface
    public interface Work<T> { T run() throws SQLException; }

    // ------------------------------------------------------------------ connection
    public static synchronized DbConfig config() { return config.copy(); }

    public static synchronized void useConfig(DbConfig c) {
        config = c.copy();
        closeQuietly();
        QueryLogger.reset();
    }

    public static synchronized Connection connection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(config.url(), config.user, config.password);
            conn.setAutoCommit(true);
        }
        return conn;
    }

    /** Opens a throw-away connection to check the settings. Returns the server version. */
    public static String test(DbConfig c) throws SQLException {
        try (Connection t = DriverManager.getConnection(c.url(), c.user, c.password);
             Statement s = t.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM EMPLOYEE")) {
            rs.next();
            return t.getMetaData().getDatabaseProductVersion();
        }
    }

    public static synchronized void closeQuietly() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) { }
        conn = null;
    }

    // ------------------------------------------------------------------ statements
    public static synchronized List<Row> query(String sql, Object... params) throws SQLException {
        long t0 = System.nanoTime();
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, params);
            List<Row> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) rows.add(Row.from(rs, md));
            }
            log(sql, params, rows.size(), t0, null);
            return rows;
        } catch (SQLException e) {
            log(sql, params, null, t0, e);
            dropIfBroken(e);
            throw e;
        }
    }

    /** First row, or null when there is none. */
    public static Row one(String sql, Object... params) throws SQLException {
        List<Row> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** INSERT / UPDATE / DELETE. Returns the number of affected rows. */
    public static synchronized int update(String sql, Object... params) throws SQLException {
        long t0 = System.nanoTime();
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, params);
            int n = ps.executeUpdate();
            log(sql, params, n, t0, null);
            return n;
        } catch (SQLException e) {
            log(sql, params, null, t0, e);
            dropIfBroken(e);
            throw e;
        }
    }

    /** INSERT that returns the new AUTO_INCREMENT key. */
    public static synchronized long insert(String sql, Object... params) throws SQLException {
        long t0 = System.nanoTime();
        try (PreparedStatement ps = connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            int n = ps.executeUpdate();
            long key = -1;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) key = keys.getLong(1);
            }
            log(sql, params, n, t0, null);
            return key;
        } catch (SQLException e) {
            log(sql, params, null, t0, e);
            dropIfBroken(e);
            throw e;
        }
    }

    /**
     * Runs several statements as one unit: either all of them are saved
     * (COMMIT) or none of them (ROLLBACK).
     */
    public static synchronized <T> T transaction(Work<T> work) throws SQLException {
        if (inTransaction) return work.run();          // already inside one
        Connection c = connection();
        long t0 = System.nanoTime();
        c.setAutoCommit(false);
        inTransaction = true;
        QueryLogger.log("TCL", "START TRANSACTION", null, ms(t0), null);
        try {
            T result = work.run();
            long t1 = System.nanoTime();
            c.commit();
            QueryLogger.log("TCL", "COMMIT", null, ms(t1), null);
            return result;
        } catch (SQLException | RuntimeException e) {
            long t1 = System.nanoTime();
            try { c.rollback(); } catch (SQLException ignored) { }
            QueryLogger.log("TCL", "ROLLBACK", null, ms(t1), null);
            throw e;
        } finally {
            inTransaction = false;
            try { c.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    // ------------------------------------------------------------------ helpers
    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object v = params[i] instanceof Secret s ? s.value() : params[i];
            if (v == null) ps.setNull(i + 1, Types.NULL);
            else if (v instanceof LocalDate d) ps.setDate(i + 1, java.sql.Date.valueOf(d));
            else if (v instanceof LocalDateTime t) ps.setTimestamp(i + 1, Timestamp.valueOf(t));
            else ps.setObject(i + 1, v);
        }
    }

    private static void log(String sql, Object[] params, Integer rows, long t0, SQLException e) {
        if (sql.toUpperCase(Locale.ROOT).contains("QUERY_LOG")) return;   // don't log the log itself
        QueryLogger.log(typeOf(sql), interpolate(sql, params), rows, ms(t0), e);
    }

    private static int ms(long t0) { return (int) Math.max(0, (System.nanoTime() - t0) / 1_000_000); }

    private static void dropIfBroken(SQLException e) {
        String state = e.getSQLState();
        if (state != null && state.startsWith("08")) closeQuietly();
    }

    static String typeOf(String sql) {
        String s = sql.stripLeading().toUpperCase(Locale.ROOT);
        for (String t : new String[]{"SELECT", "INSERT", "UPDATE", "DELETE"}) if (s.startsWith(t)) return t;
        return "OTHER";
    }

    /** SQL with the ? placeholders replaced by the actual values (for the log only). */
    static String interpolate(String sql, Object[] params) {
        StringBuilder out = new StringBuilder();
        int p = 0;
        boolean inQuote = false;
        for (char ch : sql.toCharArray()) {
            if (ch == '\'') inQuote = !inQuote;
            if (ch == '?' && !inQuote && p < params.length) out.append(literal(params[p++]));
            else out.append(ch);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static String literal(Object v) {
        if (v == null) return "NULL";
        if (v instanceof Secret) return "'••••••'";
        if (v instanceof Number || v instanceof BigDecimal) return v.toString();
        if (v instanceof Boolean b) return b ? "TRUE" : "FALSE";
        return "'" + v.toString().replace("'", "''") + "'";
    }
}
