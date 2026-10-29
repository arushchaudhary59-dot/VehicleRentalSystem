package vrs.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.*;
import vrs.db.*;
import vrs.model.Models.Payment;

/**
 * Payments. The customer can pay only after an employee has confirmed the
 * booking. This is a simulated gateway: nothing leaves the computer, we only
 * record the transaction in the PAYMENT table.
 */
public final class PaymentService {
    private PaymentService() {}

    public static final List<String> ONLINE_METHODS = List.of("UPI", "Card", "Net Banking");
    public static final List<String> ALL_METHODS = List.of("Cash", "UPI", "Card", "Net Banking");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Customer pays the outstanding balance of their confirmed booking. Returns the transaction reference. */
    public static String payBalance(int bookingId, int customerId, String method) throws SQLException {
        if (!ONLINE_METHODS.contains(method)) throw new IllegalArgumentException("Choose UPI, Card or Net Banking.");
        return Db.transaction(() -> {
            Row b = Db.one("SELECT CustomerID, BookingStatus, TotalAmount FROM BOOKING WHERE BookingID = ? FOR UPDATE", bookingId);
            if (b == null || b.i("CustomerID") != customerId) throw new IllegalArgumentException("Booking not found.");
            if (!"Confirmed".equals(b.str("BookingStatus")))
                throw new IllegalArgumentException("You can pay once the booking is confirmed by our team.");
            BigDecimal balance = b.dec("TotalAmount").subtract(paid(bookingId));
            if (balance.signum() <= 0) throw new IllegalArgumentException("This booking is already fully paid.");
            return insertPayment(bookingId, balance, method, "Success", false);
        });
    }

    /** Employee records a payment taken at the desk (cash, card machine, ...). */
    public static String record(int bookingId, BigDecimal amount, String method) throws SQLException {
        if (!ALL_METHODS.contains(method)) throw new IllegalArgumentException("Unknown payment method.");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Enter an amount greater than zero.");
        return Db.transaction(() -> {
            Row b = Db.one("SELECT BookingStatus, TotalAmount FROM BOOKING WHERE BookingID = ? FOR UPDATE", bookingId);
            if (b == null) throw new IllegalArgumentException("Booking not found.");
            String st = b.str("BookingStatus");
            if (!st.equals("Confirmed") && !st.equals("Completed"))
                throw new IllegalArgumentException("Payments can only be recorded for confirmed or completed bookings.");
            BigDecimal balance = b.dec("TotalAmount").subtract(paid(bookingId));
            if (amount.compareTo(balance) > 0)
                throw new IllegalArgumentException("Amount is more than the balance due (" + balance + ").");
            return insertPayment(bookingId, amount, method, "Success", false);
        });
    }

    public static List<Payment> all() throws SQLException {
        List<Payment> out = new ArrayList<>();
        for (Row r : Db.query(
                "SELECT p.PaymentID, p.BookingID, cu.FullName AS CustomerName, CONCAT(v.Make, ' ', v.Model) AS VehicleName, "
              + "       v.BodyType, p.Amount, p.PaymentDate, p.PaymentMethod, p.PaymentStatus, p.TransactionRef "
              + "FROM PAYMENT p "
              + "JOIN BOOKING b ON b.BookingID = p.BookingID "
              + "JOIN CUSTOMER cu ON cu.CustomerID = b.CustomerID "
              + "JOIN VEHICLE v ON v.VehicleID = b.VehicleID "
              + "ORDER BY p.PaymentDate DESC, p.PaymentID DESC")) {
            out.add(new Payment(r.i("PaymentID"), r.i("BookingID"), r.str("CustomerName"), r.str("VehicleName"),
                    r.str("BodyType"), r.dec("Amount"), r.dateTime("PaymentDate"), r.str("PaymentMethod"),
                    r.str("PaymentStatus"), r.str("TransactionRef")));
        }
        return out;
    }

    // ------------------------------------------------------------------ internals
    static BigDecimal paid(int bookingId) throws SQLException {
        return Db.one("SELECT COALESCE(SUM(Amount), 0) AS Paid FROM PAYMENT WHERE BookingID = ? AND PaymentStatus = 'Success'",
                bookingId).dec("Paid");
    }

    /** Inserts one PAYMENT row and returns its transaction reference ("" for cash). */
    static String insertPayment(int bookingId, BigDecimal amount, String method, String status, boolean refund) throws SQLException {
        String ref = reference(method, refund);
        Db.insert("INSERT INTO PAYMENT (BookingID, Amount, PaymentMethod, PaymentStatus, TransactionRef) VALUES (?, ?, ?, ?, ?)",
                bookingId, amount, method, status, ref);
        return ref == null ? "" : ref;
    }

    /** Same style as the sample data: UPI123..., CRD123..., NBK123..., refunds prefixed with RFD. Cash has none. */
    private static String reference(String method, boolean refund) {
        String prefix = switch (method) {
            case "UPI" -> "UPI";
            case "Card" -> "CRD";
            case "Net Banking" -> "NBK";
            default -> null;
        };
        if (prefix == null) return null;
        StringBuilder sb = new StringBuilder(refund ? "RFD" + prefix : prefix);
        for (int i = 0; i < 12; i++) sb.append(RANDOM.nextInt(10));
        return sb.toString();
    }
}
