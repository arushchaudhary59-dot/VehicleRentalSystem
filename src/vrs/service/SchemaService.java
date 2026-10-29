package vrs.service;

import java.sql.SQLException;
import java.util.*;
import vrs.db.*;

/** Live table information for the interactive ER diagram (read from INFORMATION_SCHEMA). */
public final class SchemaService {
    private SchemaService() {}

    public record Column(String name, String type, boolean nullable, String key, String references) {}
    public record TableInfo(String table, long rows, List<Column> columns) {}

    private static final Set<String> TABLES = Set.of("CUSTOMER", "BOOKING", "PAYMENT", "VEHICLE_CATEGORY",
            "VEHICLE", "PRICING_RULE", "EMPLOYEE");

    /** Row count of every entity table, in one query. */
    public static Map<String, Long> rowCounts() throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> names = new ArrayList<>(new TreeSet<>(TABLES));
        for (int i = 0; i < names.size(); i++)
            sql.append(i == 0 ? "" : ", ").append("(SELECT COUNT(*) FROM ").append(names.get(i)).append(") AS ").append(names.get(i));
        Row r = Db.one(sql.toString());
        Map<String, Long> out = new HashMap<>();
        for (String n : names) out.put(n, r.l(n));
        return out;
    }

    public static TableInfo describe(String table) throws SQLException {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Unknown table " + table);
        Map<String, String> fks = new HashMap<>();
        for (Row r : Db.query("SELECT COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME "
                + "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE "
                + "WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = ? AND REFERENCED_TABLE_NAME IS NOT NULL", table))
            fks.put(r.str("COLUMN_NAME").toUpperCase(), r.str("REFERENCED_TABLE_NAME").toUpperCase() + "." + r.str("REFERENCED_COLUMN_NAME"));
        List<Column> cols = new ArrayList<>();
        for (Row r : Db.query("SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY "
                + "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = ? "
                + "ORDER BY ORDINAL_POSITION", table)) {
            String name = r.str("COLUMN_NAME");
            String key = r.str("COLUMN_KEY");
            String ref = fks.get(name.toUpperCase());
            String k = "PRI".equals(key) ? "PK" : ref != null ? "FK" : "UNI".equals(key) ? "UQ" : "";
            cols.add(new Column(name, r.str("COLUMN_TYPE"), "YES".equals(r.str("IS_NULLABLE")), k, ref));
        }
        // table name comes from the whitelist above, so concatenation is safe here
        long rows = Db.one("SELECT COUNT(*) AS N FROM " + table).l("N");
        return new TableInfo(table, rows, cols);
    }
}
