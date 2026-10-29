package vrs.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import vrs.db.*;
import vrs.model.Models.*;

/**
 * Booking life-cycle:
 *   customer requests  -> Pending   (blocks the vehicle for those dates)
 *   employee confirms  -> Confirmed (customer can now pay)
 *   employee completes -> Completed
 *   customer / employee cancels -> Cancelled (any successful payments are refunded)
 *
 * The overlap rule and pricing-rule checks are enforced again by the
 * BEFORE INSERT / BEFORE UPDATE triggers on BOOKING, inside a transaction,
 * so two people can never get the same vehicle for overlapping dates.
 */
public final class BookingService {
    private BookingService() {}

    public static final int MAX_DAYS = 90;

    private static final String BOOKING_SELECT =
            "SELECT b.BookingID, b.CustomerID, cu.FullName AS CustomerName, cu.Email AS CustomerEmail, cu.Phone AS CustomerPhone, "
          + "       b.VehicleID, CONCAT(v.Make, ' ', v.Model) AS VehicleName, v.RegistrationNo, v.BodyType, v.FuelType, "
          + "       b.StartDate, b.EndDate, b.BookingStatus, b.AgreedDailyRate, b.AppliedDiscountPercent, b.TotalAmount, "
          + "       b.CreatedAt, b.HandledBy, e.FullName AS HandledByName, "
          + "       COALESCE((SELECT SUM(p.Amount) FROM PAYMENT p WHERE p.BookingID = b.BookingID AND p.PaymentStatus = 'Success'), 0) AS Paid, "
          + "       COALESCE((SELECT SUM(p.Amount) FROM PAYMENT p WHERE p.BookingID = b.BookingID AND p.PaymentStatus = 'Refunded'), 0) AS Refunded "
          + "FROM BOOKING b "
          + "JOIN CUSTOMER cu ON cu.CustomerID = b.CustomerID "
          + "JOIN VEHICLE v ON v.VehicleID = b.VehicleID "
          + "LEFT JOIN EMPLOYEE e ON e.EmployeeID = b.HandledBy ";

    // ------------------------------------------------------------------ reading
    public static List<Booking> forCustomer(int customerId) throws SQLException {
        return map(Db.query(BOOKING_SELECT + "WHERE b.CustomerID = ? ORDER BY b.StartDate DESC, b.BookingID DESC", customerId));
    }

    public static List<Booking> all() throws SQLException {
        return map(Db.query(BOOKING_SELECT + "ORDER BY b.BookingID DESC"));
    }

    public static List<Booking> pending(int limit) throws SQLException {
        return map(Db.query(BOOKING_SELECT + "WHERE b.BookingStatus = 'Pending' ORDER BY b.CreatedAt LIMIT ?", limit));
    }

    public static Booking byId(int id) throws SQLException {
        List<Booking> l = map(Db.query(BOOKING_SELECT + "WHERE b.BookingID = ?", id));
        return l.isEmpty() ? null : l.get(0);
    }

    /** Pricing rule for this vehicle's category, the number of days and the start date. */
    public static Quote quote(int vehicleId, LocalDate start, LocalDate end) throws SQLException {
        checkDates(start, end, false);
        int days = (int) ChronoUnit.DAYS.between(start, end);
        Row r = Db.one("SELECT r.PricingRuleID, r.DailyRate, r.DiscountPercent, r.MinDays, r.MaxDays "
                + "FROM VEHICLE v JOIN PRICING_RULE r ON r.CategoryID = v.CategoryID "
                + "WHERE v.VehicleID = ? AND ? >= r.MinDays AND (r.MaxDays IS NULL OR ? <= r.MaxDays) "
                + "  AND ? >= r.EffectiveFrom AND (r.EffectiveTo IS NULL OR ? <= r.EffectiveTo)",
                vehicleId, days, days, start, start);
        if (r == null) throw new IllegalArgumentException("No pricing rule covers " + days + " day(s) for this vehicle's category.");
        return new Quote(r.i("PricingRuleID"), r.dec("DailyRate"), r.dec("DiscountPercent"), days,
                r.i("MinDays"), r.intOrNull("MaxDays"));
    }

    // ------------------------------------------------------------------ customer actions
    /** Customer asks for a vehicle. Creates a Pending booking. */
    public static int request(int customerId, int vehicleId, LocalDate start, LocalDate end) throws SQLException {
        checkDates(start, end, true);
        return Db.transaction(() -> insert(customerId, vehicleId, start, end, "Pending", null));
    }

    /** Customer cancels their own Pending / Confirmed booking before it starts. */
    public static void cancelByCustomer(int bookingId, int customerId) throws SQLException {
        Db.transaction(() -> {
            Row b = Db.one("SELECT CustomerID, BookingStatus, StartDate FROM BOOKING WHERE BookingID = ? FOR UPDATE", bookingId);
            if (b == null || b.i("CustomerID") != customerId) throw new IllegalArgumentException("Booking not found.");
            String st = b.str("BookingStatus");
            if (!st.equals("Pending") && !st.equals("Confirmed"))
                throw new IllegalArgumentException("Only pending or confirmed bookings can be cancelled.");
            if (b.date("StartDate").isBefore(LocalDate.now()))
                throw new IllegalArgumentException("This rental has already started. Please contact the rental desk.");
            Db.update("UPDATE BOOKING SET BookingStatus = 'Cancelled' WHERE BookingID = ?", bookingId);
            refundIfPaid(bookingId);
            return null;
        });
    }

    // ------------------------------------------------------------------ employee actions (CRUD)
    /** CREATE: a booking entered by staff (e.g. walk-in customer). */
    public static int create(int customerId, int vehicleId, LocalDate start, LocalDate end, String status, int employeeId) throws SQLException {
        checkDates(start, end, false);
        if (!status.equals("Pending") && !status.equals("Confirmed"))
            throw new IllegalArgumentException("A new booking must be Pending or Confirmed.");
        return Db.transaction(() -> insert(customerId, vehicleId, start, end, status, employeeId));
    }

    /** Pending -> Confirmed. */
    public static void confirm(int bookingId, int employeeId) throws SQLException {
        changeStatus(bookingId, "Confirmed", employeeId, Set.of("Pending"));
    }

    /** Confirmed -> Completed (vehicle returned). */
    public static void complete(int bookingId, int employeeId) throws SQLException {
        changeStatus(bookingId, "Completed", employeeId, Set.of("Confirmed"));
    }

    /** Pending / Confirmed -> Cancelled, refunding successful payments. */
    public static void cancel(int bookingId, int employeeId) throws SQLException {
        changeStatus(bookingId, "Cancelled", employeeId, Set.of("Pending", "Confirmed"));
    }

    /**
     * UPDATE: change vehicle, dates and/or status. If the vehicle or dates
     * change, the price is recalculated from the current pricing rule.
     */
    public static void update(int bookingId, int vehicleId, LocalDate start, LocalDate end, String status, int employeeId) throws SQLException {
        checkDates(start, end, false);
        Db.transaction(() -> {
            Booking cur = byId(bookingId);
            if (cur == null) throw new IllegalArgumentException("Booking not found.");
            if (cur.status().equals("Cancelled") && !status.equals("Cancelled"))
                throw new IllegalArgumentException("A cancelled booking cannot be reopened (any payment was refunded). Create a new booking instead.");
            boolean repriced = cur.vehicleId() != vehicleId || !cur.start().equals(start) || !cur.end().equals(end);
            if (repriced) {
                if (cur.status().equals("Cancelled") || cur.status().equals("Completed"))
                    throw new IllegalArgumentException("Vehicle and dates of a " + cur.status().toLowerCase() + " booking cannot be changed.");
                Quote q = quote(vehicleId, start, end);
                if (q.total().compareTo(cur.paid()) < 0)
                    throw new IllegalArgumentException("The new total (" + q.total() + ") is less than what the customer already paid (" + cur.paid() + "). Cancel and rebook instead.");
                Row veh = Db.one("SELECT OperationalStatus FROM VEHICLE WHERE VehicleID = ?", vehicleId);
                if (cur.vehicleId() != vehicleId && !"Available".equals(veh.str("OperationalStatus")))
                    throw new IllegalArgumentException("The selected vehicle is " + veh.str("OperationalStatus").toLowerCase() + ".");
                Db.update("UPDATE BOOKING SET VehicleID = ?, StartDate = ?, EndDate = ?, PricingRuleID = ?, "
                        + "AgreedDailyRate = ?, AppliedDiscountPercent = ?, "
                        + "TotalAmount = ROUND(DATEDIFF(?, ?) * ? * (1 - ? / 100), 2), HandledBy = ? WHERE BookingID = ?",
                        vehicleId, start, end, q.ruleId(), q.dailyRate(), q.discountPercent(),
                        end, start, q.dailyRate(), q.discountPercent(), employeeId, bookingId);
            }
            if (!status.equals(cur.status())) {
                Db.update("UPDATE BOOKING SET BookingStatus = ?, HandledBy = ? WHERE BookingID = ?", status, employeeId, bookingId);
                if (status.equals("Cancelled")) refundIfPaid(bookingId);
            }
            return null;
        });
    }

    /** DELETE: only allowed when no payment rows reference the booking (FK RESTRICT). */
    public static void delete(int bookingId) throws SQLException {
        Db.transaction(() -> {
            Row p = Db.one("SELECT COUNT(*) AS N FROM PAYMENT WHERE BookingID = ?", bookingId);
            if (p.l("N") > 0)
                throw new IllegalArgumentException("Booking #" + bookingId + " has " + p.l("N")
                        + " payment record(s). Payment history must be kept, so cancel it instead of deleting.");
            int n = Db.update("DELETE FROM BOOKING WHERE BookingID = ?", bookingId);
            if (n == 0) throw new IllegalArgumentException("Booking not found.");
            return null;
        });
    }

    // ------------------------------------------------------------------ internals
    private static int insert(int customerId, int vehicleId, LocalDate start, LocalDate end, String status, Integer employeeId) throws SQLException {
        Row v = Db.one("SELECT OperationalStatus FROM VEHICLE WHERE VehicleID = ?", vehicleId);
        if (v == null) throw new IllegalArgumentException("Vehicle not found.");
        if (!"Available".equals(v.str("OperationalStatus")))
            throw new IllegalArgumentException("This vehicle is " + v.str("OperationalStatus").toLowerCase() + " and cannot be booked.");
        Quote q = quote(vehicleId, start, end);
        // TotalAmount is computed by MySQL with the same formula as the CHECK constraint.
        return (int) Db.insert(
                "INSERT INTO BOOKING (CustomerID, VehicleID, PricingRuleID, StartDate, EndDate, BookingStatus, "
              + "AgreedDailyRate, AppliedDiscountPercent, TotalAmount, HandledBy) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ROUND(DATEDIFF(?, ?) * ? * (1 - ? / 100), 2), ?)",
                customerId, vehicleId, q.ruleId(), start, end, status, q.dailyRate(), q.discountPercent(),
                end, start, q.dailyRate(), q.discountPercent(), employeeId);
    }

    private static void changeStatus(int bookingId, String to, int employeeId, Set<String> allowedFrom) throws SQLException {
        Db.transaction(() -> {
            Row b = Db.one("SELECT BookingStatus FROM BOOKING WHERE BookingID = ? FOR UPDATE", bookingId);
            if (b == null) throw new IllegalArgumentException("Booking not found.");
            String from = b.str("BookingStatus");
            if (!allowedFrom.contains(from))
                throw new IllegalArgumentException("Booking #" + bookingId + " is " + from + "; it cannot become " + to + ".");
            Db.update("UPDATE BOOKING SET BookingStatus = ?, HandledBy = ? WHERE BookingID = ?", to, employeeId, bookingId);
            if (to.equals("Cancelled")) refundIfPaid(bookingId);
            return null;
        });
    }

    /** Adds a 'Refunded' payment row for whatever was paid and not yet refunded. */
    private static void refundIfPaid(int bookingId) throws SQLException {
        Row s = Db.one("SELECT COALESCE(SUM(CASE WHEN PaymentStatus = 'Success' THEN Amount END), 0) AS Paid, "
                + "COALESCE(SUM(CASE WHEN PaymentStatus = 'Refunded' THEN Amount END), 0) AS Refunded "
                + "FROM PAYMENT WHERE BookingID = ?", bookingId);
        BigDecimal net = s.dec("Paid").subtract(s.dec("Refunded"));
        if (net.signum() <= 0) return;
        Row last = Db.one("SELECT PaymentMethod FROM PAYMENT WHERE BookingID = ? AND PaymentStatus = 'Success' "
                + "ORDER BY PaymentDate DESC, PaymentID DESC LIMIT 1", bookingId);
        String method = last == null ? "Cash" : last.str("PaymentMethod");
        PaymentService.insertPayment(bookingId, net, method, "Refunded", true);
    }

    private static void checkDates(LocalDate start, LocalDate end, boolean futureOnly) {
        if (start == null || end == null) throw new IllegalArgumentException("Choose both dates.");
        if (!end.isAfter(start)) throw new IllegalArgumentException("Return date must be after the pick-up date.");
        if (futureOnly && start.isBefore(LocalDate.now())) throw new IllegalArgumentException("Pick-up date cannot be in the past.");
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) throw new IllegalArgumentException("Bookings are limited to " + MAX_DAYS + " days.");
    }

    private static List<Booking> map(List<Row> rows) {
        List<Booking> out = new ArrayList<>();
        for (Row r : rows) {
            out.add(new Booking(r.i("BookingID"), r.i("CustomerID"), r.str("CustomerName"), r.str("CustomerEmail"),
                    r.str("CustomerPhone"), r.i("VehicleID"), r.str("VehicleName"), r.str("RegistrationNo"),
                    r.str("BodyType"), r.str("FuelType"), r.date("StartDate"), r.date("EndDate"), r.str("BookingStatus"),
                    r.dec("AgreedDailyRate"), r.dec("AppliedDiscountPercent"), r.dec("TotalAmount"),
                    r.dec("Paid"), r.dec("Refunded"), r.dateTime("CreatedAt"), r.intOrNull("HandledBy"), r.str("HandledByName")));
        }
        return out;
    }
}
