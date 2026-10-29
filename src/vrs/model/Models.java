package vrs.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;

/** Plain data records used by the services and the screens. */
public final class Models {
    private Models() {}

    public record Vehicle(int id, String reg, String make, String model, int categoryId, String category,
                          String status, String bodyType, String fuel, String transmission, int seats,
                          int upcomingBookings, int totalBookings) {
        public String name() { return make + " " + model; }
        public boolean electric() { return "Electric".equals(fuel); }
    }

    /** A vehicle that is free for the searched dates, with its price for that duration. */
    public record Offer(Vehicle vehicle, int ruleId, BigDecimal dailyRate, BigDecimal discountPercent, int days) {
        public BigDecimal subtotal() { return dailyRate.multiply(BigDecimal.valueOf(days)); }
        public BigDecimal total() {
            return subtotal().multiply(BigDecimal.ONE.subtract(discountPercent.movePointLeft(2)))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        public BigDecimal saving() { return subtotal().subtract(total()); }
    }

    /** Price for one vehicle and date range, from the matching PRICING_RULE. */
    public record Quote(int ruleId, BigDecimal dailyRate, BigDecimal discountPercent, int days,
                        int minDays, Integer maxDays) {
        public BigDecimal subtotal() { return dailyRate.multiply(BigDecimal.valueOf(days)); }
        public BigDecimal total() {
            return subtotal().multiply(BigDecimal.ONE.subtract(discountPercent.movePointLeft(2)))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        public BigDecimal saving() { return subtotal().subtract(total()); }
        public String tier() {
            return maxDays == null ? minDays + "+ days" : minDays == maxDays ? minDays + " day" : minDays + "–" + maxDays + " days";
        }
    }

    public record Booking(int id, int customerId, String customerName, String customerEmail, String customerPhone,
                          int vehicleId, String vehicleName, String reg, String bodyType, String fuel,
                          LocalDate start, LocalDate end, String status,
                          BigDecimal dailyRate, BigDecimal discountPercent, BigDecimal total,
                          BigDecimal paid, BigDecimal refunded, LocalDateTime createdAt,
                          Integer handledBy, String handledByName) {
        public int days() { return (int) ChronoUnit.DAYS.between(start, end); }
        public BigDecimal balance() { return total.subtract(paid).max(BigDecimal.ZERO); }
        public boolean fullyPaid() { return paid.compareTo(total) >= 0; }
        public boolean isPast() { return !end.isAfter(LocalDate.now()); }
        public boolean electric() { return "Electric".equals(fuel); }
    }

    public record Payment(int id, int bookingId, String customerName, String vehicleName, String bodyType,
                          BigDecimal amount, LocalDateTime date, String method, String status, String txnRef) {}

    public record Customer(int id, String name, String email, String phone, String licence, boolean hasLogin) {
        @Override public String toString() { return name + "  ·  " + email; }
    }

    public record LogEntry(long id, LocalDateTime at, String role, String email, String type, String sql,
                           Integer rows, Integer ms, String status, String error) {}

    public record Category(int id, String name) {
        @Override public String toString() { return name; }
    }
}
