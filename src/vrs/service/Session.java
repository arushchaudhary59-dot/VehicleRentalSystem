package vrs.service;

/** Who is logged in right now (one user at a time in a desktop app). */
public final class Session {
    public enum Role { CUSTOMER, EMPLOYEE }

    /** detail = phone for customers, designation for employees. */
    public record User(Role role, int id, String name, String email, String detail) {
        public String initials() {
            String[] p = name.trim().split("\\s+");
            String s = p[0].substring(0, 1) + (p.length > 1 ? p[p.length - 1].substring(0, 1) : "");
            return s.toUpperCase();
        }
        public String firstName() { return name.trim().split("\\s+")[0]; }
    }

    private static volatile User current;

    private Session() {}

    public static User current() { return current; }
    public static void login(User u) { current = u; }
    public static void logout() { current = null; }

    public static User require(Role role) {
        User u = current;
        if (u == null || u.role() != role) throw new IllegalStateException("Please log in as " + role.name().toLowerCase() + ".");
        return u;
    }
}
