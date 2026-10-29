package vrs.db;

import java.sql.SQLException;
import java.util.Map;

/** Turns database exceptions into messages a user can understand. */
public final class DbErrors {
    private DbErrors() {}

    private static final Map<String, String> UNIQUE = Map.of(
            "uq_customer_email", "This e-mail is already registered.",
            "uq_customer_phone", "This phone number is already registered.",
            "uq_customer_licence", "This driving licence number is already registered.",
            "uq_vehicle_reg", "A vehicle with this registration number already exists.",
            "uq_employee_email", "An employee with this e-mail already exists.",
            "uq_payment_txn", "Duplicate transaction reference. Please try again.");

    public static String friendly(Throwable t) {
        while (t.getCause() != null && !(t instanceof SQLException)) t = t.getCause();
        if (!(t instanceof SQLException e)) {
            String m = t.getMessage();
            return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        int code = e.getErrorCode();

        if ("45000".equals(state)) return msg;                       // our triggers (SIGNAL)
        if (state.startsWith("08") || msg.contains("Communications link failure"))
            return "Cannot reach MySQL. Make sure the MySQL server is running.";
        switch (code) {
            case 1045: return "MySQL refused the login: wrong username or password (Database settings).";
            case 1049: return "The database does not exist yet. Import sql/vehicle_rental_system.sql first.";
            case 1146: case 1054:
                return "The database is missing tables/columns of version 2. Re-import sql/vehicle_rental_system.sql.";
            case 1062:
                for (var en : UNIQUE.entrySet()) if (msg.contains(en.getKey())) return en.getValue();
                return "This value already exists.";
            case 1451: return "This record is used by other records (for example payments), so it cannot be deleted.";
            case 1452: return "The referenced record does not exist.";
            case 3819: return "A value is not allowed by the database rules (" + constraintName(msg) + ").";
            case 1205: return "The record is busy (lock timeout). Please try again.";
            default: return msg.isBlank() ? "Database error " + code : msg;
        }
    }

    private static String constraintName(String msg) {
        int a = msg.indexOf('\'');
        int b = a < 0 ? -1 : msg.indexOf('\'', a + 1);
        return a >= 0 && b > a ? msg.substring(a + 1, b) : "check constraint";
    }
}
