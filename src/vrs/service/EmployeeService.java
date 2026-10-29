package vrs.service;

import java.sql.SQLException;
import java.util.*;
import vrs.db.*;

/** Staff accounts. Only a Manager may add employees (checked here and in the UI). */
public final class EmployeeService {
    private EmployeeService() {}

    public static final List<String> DESIGNATIONS = List.of("Rental Operator", "Manager");

    public record Employee(int id, String name, String email, String phone, String designation, int handled) {}

    public static List<Employee> all() throws SQLException {
        List<Employee> out = new ArrayList<>();
        for (Row r : Db.query("SELECT e.EmployeeID, e.FullName, e.Email, e.Phone, e.Designation, "
                + "(SELECT COUNT(*) FROM BOOKING b WHERE b.HandledBy = e.EmployeeID) AS Handled "
                + "FROM EMPLOYEE e ORDER BY e.EmployeeID"))
            out.add(new Employee(r.i("EmployeeID"), r.str("FullName"), r.str("Email"), r.str("Phone"),
                    r.str("Designation"), r.i("Handled")));
        return out;
    }

    /** Adds a staff account with a starting password the manager hands to the new employee. */
    public static int add(String name, String email, String phone, String designation, String password) throws SQLException {
        requireManager();
        name = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        email = email == null ? "" : email.trim().toLowerCase();
        if (name.length() < 2) throw new IllegalArgumentException("Enter the employee's full name.");
        if (!email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) throw new IllegalArgumentException("Enter a valid e-mail address.");
        String ph = phone == null || phone.isBlank() ? null : AuthService.normalisePhone(phone);
        if (!DESIGNATIONS.contains(designation)) throw new IllegalArgumentException("Choose a designation.");
        if (password == null || password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        String salt = AuthService.newSalt();
        return (int) Db.insert("INSERT INTO EMPLOYEE (FullName, Email, Phone, Designation, PasswordSalt, PasswordHash) "
                + "VALUES (?, ?, ?, ?, ?, ?)", name, email, ph, designation,
                Db.secret(salt), Db.secret(AuthService.hash(salt, password)));
    }

    /** Sets a new password for an employee (e.g. they forgot it). */
    public static void resetPassword(int employeeId, String password) throws SQLException {
        requireManager();
        if (password == null || password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        String salt = AuthService.newSalt();
        Db.update("UPDATE EMPLOYEE SET PasswordSalt = ?, PasswordHash = ? WHERE EmployeeID = ?",
                Db.secret(salt), Db.secret(AuthService.hash(salt, password)), employeeId);
    }

    private static void requireManager() {
        Session.User u = Session.require(Session.Role.EMPLOYEE);
        if (!"Manager".equals(u.detail())) throw new IllegalArgumentException("Only a Manager can manage staff accounts.");
    }
}
