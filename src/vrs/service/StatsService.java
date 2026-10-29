package vrs.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import vrs.db.*;
import vrs.model.Models.Customer;
import vrs.model.VehicleTypes;

/** Numbers for the employee dashboard. */
public final class StatsService {
    private StatsService() {}

    public record Summary(int pending, int onRoad, BigDecimal collectedThisMonth, int available, int fleet,
                          int pickupsToday, int returnsToday) {}

    /** One bar of the chart. */
    public record TypeStat(String bodyType, int rentals, BigDecimal revenue) {}

    public static Summary summary() throws SQLException {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        Row r = Db.one(
                "SELECT "
              + " (SELECT COUNT(*) FROM BOOKING WHERE BookingStatus = 'Pending') AS Pending, "
              + " (SELECT COUNT(*) FROM BOOKING WHERE BookingStatus = 'Confirmed' AND StartDate <= CURDATE() AND EndDate > CURDATE()) AS OnRoad, "
              + " (SELECT COALESCE(SUM(CASE WHEN PaymentStatus = 'Success' THEN Amount WHEN PaymentStatus = 'Refunded' THEN -Amount END), 0) "
              + "    FROM PAYMENT WHERE PaymentDate >= ? AND PaymentDate < ?) AS Collected, "
              + " (SELECT COUNT(*) FROM VEHICLE WHERE OperationalStatus = 'Available') AS Available, "
              + " (SELECT COUNT(*) FROM VEHICLE WHERE OperationalStatus <> 'Retired') AS Fleet, "
              + " (SELECT COUNT(*) FROM BOOKING WHERE BookingStatus = 'Confirmed' AND StartDate = CURDATE()) AS PickupsToday, "
              + " (SELECT COUNT(*) FROM BOOKING WHERE BookingStatus = 'Confirmed' AND EndDate = CURDATE()) AS ReturnsToday",
                first, first.plusMonths(1));
        return new Summary(r.i("Pending"), r.i("OnRoad"), r.dec("Collected"), r.i("Available"), r.i("Fleet"),
                r.i("PickupsToday"), r.i("ReturnsToday"));
    }

    /**
     * Sales per vehicle type for one month: confirmed + completed rentals whose
     * pick-up date falls in that month. Every body type is returned (0 if none).
     */
    public static List<TypeStat> salesByType(YearMonth month) throws SQLException {
        Map<String, TypeStat> map = new LinkedHashMap<>();
        for (String t : VehicleTypes.ALL) map.put(t, new TypeStat(t, 0, BigDecimal.ZERO));
        for (Row r : Db.query(
                "SELECT v.BodyType, COUNT(*) AS Rentals, SUM(b.TotalAmount) AS Revenue "
              + "FROM BOOKING b JOIN VEHICLE v ON v.VehicleID = b.VehicleID "
              + "WHERE b.BookingStatus IN ('Confirmed', 'Completed') AND b.StartDate >= ? AND b.StartDate < ? "
              + "GROUP BY v.BodyType",
                month.atDay(1), month.plusMonths(1).atDay(1))) {
            map.put(r.str("BodyType"), new TypeStat(r.str("BodyType"), r.i("Rentals"), r.dec("Revenue")));
        }
        return new ArrayList<>(map.values());
    }

    public static List<Customer> customers() throws SQLException {
        List<Customer> out = new ArrayList<>();
        for (Row r : Db.query("SELECT CustomerID, FullName, Email, Phone, DrivingLicenceNo, "
                + "(PasswordHash IS NOT NULL) AS HasLogin FROM CUSTOMER ORDER BY FullName"))
            out.add(new Customer(r.i("CustomerID"), r.str("FullName"), r.str("Email"), r.str("Phone"),
                    r.str("DrivingLicenceNo"), r.i("HasLogin") == 1));
        return out;
    }

    /** Walk-in customer registered by staff (no password; they can activate it later by signing up). */
    public static int addCustomer(String name, String email, String phone, String licence) throws SQLException {
        name = name == null ? "" : name.trim();
        if (name.length() < 2) throw new IllegalArgumentException("Enter the customer's full name.");
        if (email == null || !email.trim().matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$"))
            throw new IllegalArgumentException("Enter a valid e-mail.");
        String ph = AuthService.normalisePhone(phone);
        String lic = licence == null || licence.isBlank() ? null : licence.trim().toUpperCase().replace(" ", "");
        return (int) Db.insert("INSERT INTO CUSTOMER (FullName, Email, Phone, DrivingLicenceNo) VALUES (?, ?, ?, ?)",
                name, email.trim().toLowerCase(), ph, lic);
    }
}
