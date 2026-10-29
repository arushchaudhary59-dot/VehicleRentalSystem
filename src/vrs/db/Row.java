package vrs.db;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** One result row. Column names are case-insensitive. */
public final class Row {
    private final Map<String, Object> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static Row from(ResultSet rs, ResultSetMetaData md) throws SQLException {
        Row r = new Row();
        for (int i = 1; i <= md.getColumnCount(); i++) r.values.put(md.getColumnLabel(i), rs.getObject(i));
        return r;
    }

    public Object get(String col) {
        if (!values.containsKey(col)) throw new IllegalArgumentException("No column " + col + " in " + values.keySet());
        return values.get(col);
    }

    public boolean isNull(String col) { return get(col) == null; }

    public String str(String col) {
        Object v = get(col);
        return v == null ? null : v.toString();
    }

    public int i(String col) {
        Object v = get(col);
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        return Integer.parseInt(v.toString());
    }

    public Integer intOrNull(String col) { return get(col) == null ? null : i(col); }

    public long l(String col) {
        Object v = get(col);
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    public BigDecimal dec(String col) {
        Object v = get(col);
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }

    public LocalDate date(String col) {
        Object v = get(col);
        if (v == null) return null;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof LocalDate d) return d;
        if (v instanceof Timestamp t) return t.toLocalDateTime().toLocalDate();
        if (v instanceof LocalDateTime t) return t.toLocalDate();
        return LocalDate.parse(v.toString().substring(0, 10));
    }

    public LocalDateTime dateTime(String col) {
        Object v = get(col);
        if (v == null) return null;
        if (v instanceof Timestamp t) return t.toLocalDateTime();
        if (v instanceof LocalDateTime t) return t;
        if (v instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
        if (v instanceof LocalDate d) return d.atStartOfDay();
        if (v instanceof OffsetDateTime o) return o.toLocalDateTime();
        return LocalDateTime.parse(v.toString().replace(' ', 'T'));
    }

    @Override public String toString() { return values.toString(); }
}
