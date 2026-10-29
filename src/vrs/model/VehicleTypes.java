package vrs.model;

import java.util.List;

/** Allowed values of the descriptive VEHICLE columns (same as the CHECK constraints). */
public final class VehicleTypes {
    private VehicleTypes() {}

    public static final List<String> CAR = List.of("Hatchback", "Sedan", "SUV", "MUV");
    public static final List<String> BIKE = List.of("Cruiser", "Sports", "Commuter");
    public static final List<String> SCOOTER = List.of("Scooter");
    public static final List<String> ALL = List.of("Hatchback", "Sedan", "SUV", "MUV", "Cruiser", "Sports", "Commuter", "Scooter");

    public static final List<String> FUELS = List.of("Petrol", "Diesel", "Electric", "CNG");
    public static final List<String> TRANSMISSIONS = List.of("Manual", "Automatic");
    public static final List<String> STATUSES = List.of("Available", "Under Maintenance", "Retired");

    /** Body types that make sense for a category name (Car / Bike / Scooter). */
    public static List<String> forCategory(String category) {
        if (category == null) return ALL;
        return switch (category.toLowerCase()) {
            case "car" -> CAR;
            case "bike" -> BIKE;
            case "scooter" -> SCOOTER;
            default -> ALL;
        };
    }
}
