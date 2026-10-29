package vrs.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import vrs.db.*;
import vrs.model.Models.*;
import vrs.model.VehicleTypes;

/** Vehicle search (customers) and fleet management (employees). */
public final class VehicleService {
    private VehicleService() {}

    private static final String VEHICLE_COLS =
            "v.VehicleID, v.RegistrationNo, v.Make, v.Model, v.CategoryID, c.CategoryName, v.OperationalStatus, "
          + "v.BodyType, v.FuelType, v.Transmission, v.Seats";

    /**
     * Vehicles that can be rented for [start, end):
     *   - operational status is 'Available'
     *   - no non-cancelled booking overlaps   (ExistingStart < NewEnd AND ExistingEnd > NewStart)
     *   - a pricing rule exists for the category, duration and start date
     * Pending requests also block the vehicle.
     */
    public static List<Offer> search(LocalDate start, LocalDate end) throws SQLException {
        int days = (int) ChronoUnit.DAYS.between(start, end);
        List<Row> rows = Db.query(
                "SELECT " + VEHICLE_COLS + ", r.PricingRuleID, r.DailyRate, r.DiscountPercent "
              + "FROM VEHICLE v "
              + "JOIN VEHICLE_CATEGORY c ON c.CategoryID = v.CategoryID "
              + "JOIN PRICING_RULE r ON r.CategoryID = v.CategoryID "
              + "  AND ? >= r.MinDays AND (r.MaxDays IS NULL OR ? <= r.MaxDays) "
              + "  AND ? >= r.EffectiveFrom AND (r.EffectiveTo IS NULL OR ? <= r.EffectiveTo) "
              + "WHERE v.OperationalStatus = 'Available' "
              + "  AND NOT EXISTS (SELECT 1 FROM BOOKING b WHERE b.VehicleID = v.VehicleID "
              + "                  AND b.BookingStatus <> 'Cancelled' AND b.StartDate < ? AND b.EndDate > ?) "
              + "ORDER BY r.DailyRate, v.Make, v.Model",
                days, days, start, start, end, start);
        List<Offer> out = new ArrayList<>();
        for (Row r : rows)
            out.add(new Offer(vehicle(r, 0, 0), r.i("PricingRuleID"), r.dec("DailyRate"), r.dec("DiscountPercent"), days));
        return out;
    }

    /** All vehicles with booking counts, for the employee fleet screen. */
    public static List<Vehicle> listAll() throws SQLException {
        List<Row> rows = Db.query(
                "SELECT " + VEHICLE_COLS + ", "
              + "  (SELECT COUNT(*) FROM BOOKING b WHERE b.VehicleID = v.VehicleID "
              + "     AND b.BookingStatus IN ('Pending', 'Confirmed') AND b.EndDate > CURDATE()) AS Upcoming, "
              + "  (SELECT COUNT(*) FROM BOOKING b WHERE b.VehicleID = v.VehicleID) AS TotalBookings "
              + "FROM VEHICLE v JOIN VEHICLE_CATEGORY c ON c.CategoryID = v.CategoryID "
              + "ORDER BY v.VehicleID");
        List<Vehicle> out = new ArrayList<>();
        for (Row r : rows) out.add(vehicle(r, r.i("Upcoming"), r.i("TotalBookings")));
        return out;
    }

    public static List<Category> categories() throws SQLException {
        List<Category> out = new ArrayList<>();
        for (Row r : Db.query("SELECT CategoryID, CategoryName FROM VEHICLE_CATEGORY ORDER BY CategoryID"))
            out.add(new Category(r.i("CategoryID"), r.str("CategoryName")));
        return out;
    }

    public static int add(String reg, String make, String model, Category cat, String body, String fuel,
                          String transmission, int seats) throws SQLException {
        validate(reg, make, model, cat, body, seats);
        return (int) Db.insert(
                "INSERT INTO VEHICLE (RegistrationNo, Make, Model, CategoryID, OperationalStatus, BodyType, FuelType, Transmission, Seats) "
              + "VALUES (?, ?, ?, ?, 'Available', ?, ?, ?, ?)",
                normReg(reg), make.trim(), model.trim(), cat.id(), body, fuel, transmission, seats);
    }

    public static void update(int id, String reg, String make, String model, Category cat, String body, String fuel,
                              String transmission, int seats) throws SQLException {
        validate(reg, make, model, cat, body, seats);
        Db.transaction(() -> {
            Row cur = Db.one("SELECT CategoryID, (SELECT COUNT(*) FROM BOOKING b WHERE b.VehicleID = v.VehicleID) AS N "
                    + "FROM VEHICLE v WHERE v.VehicleID = ?", id);
            if (cur == null) throw new IllegalArgumentException("Vehicle no longer exists.");
            if (cur.i("CategoryID") != cat.id() && cur.l("N") > 0)
                throw new IllegalArgumentException("The category cannot change: this vehicle already has bookings priced for its current category.");
            Db.update("UPDATE VEHICLE SET RegistrationNo = ?, Make = ?, Model = ?, CategoryID = ?, BodyType = ?, "
                    + "FuelType = ?, Transmission = ?, Seats = ? WHERE VehicleID = ?",
                    normReg(reg), make.trim(), model.trim(), cat.id(), body, fuel, transmission, seats, id);
            return null;
        });
    }

    public static void setStatus(int id, String status) throws SQLException {
        if (!VehicleTypes.STATUSES.contains(status)) throw new IllegalArgumentException("Unknown status " + status);
        Db.update("UPDATE VEHICLE SET OperationalStatus = ? WHERE VehicleID = ?", status, id);
    }

    // ------------------------------------------------------------------ helpers
    static Vehicle vehicle(Row r, int upcoming, int total) {
        return new Vehicle(r.i("VehicleID"), r.str("RegistrationNo"), r.str("Make"), r.str("Model"),
                r.i("CategoryID"), r.str("CategoryName"), r.str("OperationalStatus"), r.str("BodyType"),
                r.str("FuelType"), r.str("Transmission"), r.i("Seats"), upcoming, total);
    }

    private static void validate(String reg, String make, String model, Category cat, String body, int seats) {
        if (reg == null || reg.isBlank()) throw new IllegalArgumentException("Registration number is required.");
        if (make == null || make.isBlank() || model == null || model.isBlank())
            throw new IllegalArgumentException("Make and model are required.");
        if (cat == null) throw new IllegalArgumentException("Choose a category.");
        if (!VehicleTypes.forCategory(cat.name()).contains(body))
            throw new IllegalArgumentException(body + " is not a valid type for category " + cat.name() + ".");
        if (seats < 1 || seats > 12) throw new IllegalArgumentException("Seats must be between 1 and 12.");
    }

    /** "tn23ab1234" -> "TN 23 AB 1234" (keeps already-spaced input as typed, upper-cased). */
    static String normReg(String reg) {
        String s = reg.trim().toUpperCase().replaceAll("\\s+", " ");
        if (!s.contains(" ") && s.matches("[A-Z]{2}\\d{1,2}[A-Z]{1,3}\\d{1,4}"))
            s = s.replaceAll("^([A-Z]{2})(\\d{1,2})([A-Z]{1,3})(\\d{1,4})$", "$1 $2 $3 $4");
        return s;
    }
}
