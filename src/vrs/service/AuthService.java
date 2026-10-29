package vrs.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import vrs.db.*;
import vrs.service.Session.Role;
import vrs.service.Session.User;

/**
 * Sign-up and login for customers and employees.
 * Passwords are never stored: we store a random salt and SHA-256(salt + password).
 */
public final class AuthService {
    private AuthService() {}

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public static User loginCustomer(String email, String password) throws SQLException {
        Row r = Db.one("SELECT CustomerID, FullName, Email, Phone, PasswordSalt, PasswordHash "
                + "FROM CUSTOMER WHERE Email = ?", clean(email));
        if (r == null) throw new IllegalArgumentException("No customer account with this e-mail. Create one first.");
        if (r.isNull("PasswordHash"))
            throw new IllegalArgumentException("This customer was registered at the counter. Use Sign up with the same e-mail and phone to set a password.");
        check(password, r.str("PasswordSalt"), r.str("PasswordHash"));
        User u = new User(Role.CUSTOMER, r.i("CustomerID"), r.str("FullName"), r.str("Email"), r.str("Phone"));
        Session.login(u);
        return u;
    }

    public static User loginEmployee(String email, String password) throws SQLException {
        Row r = Db.one("SELECT EmployeeID, FullName, Email, Designation, PasswordSalt, PasswordHash "
                + "FROM EMPLOYEE WHERE Email = ?", clean(email));
        if (r == null) throw new IllegalArgumentException("No employee account with this e-mail.");
        check(password, r.str("PasswordSalt"), r.str("PasswordHash"));
        User u = new User(Role.EMPLOYEE, r.i("EmployeeID"), r.str("FullName"), r.str("Email"), r.str("Designation"));
        Session.login(u);
        return u;
    }

    /**
     * Creates a customer account. If staff already registered this person at
     * the counter (same e-mail and phone, no password yet) the existing record
     * is activated instead of creating a duplicate.
     */
    public static User signUp(String name, String email, String phone, String licence, String password) throws SQLException {
        name = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        email = clean(email);
        phone = normalisePhone(phone);
        licence = licence == null || licence.isBlank() ? null : licence.trim().toUpperCase().replace(" ", "");
        if (name.length() < 2) throw new IllegalArgumentException("Please enter your full name.");
        if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("Please enter a valid e-mail address.");
        if (password == null || password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");

        String salt = newSalt();
        String hash = hash(salt, password);
        final String fName = name, fEmail = email, fPhone = phone, fLicence = licence;
        long id = Db.transaction(() -> {
            Row existing = Db.one("SELECT CustomerID, Phone, PasswordHash FROM CUSTOMER WHERE Email = ?", fEmail);
            if (existing != null && existing.isNull("PasswordHash") && fPhone.equals(existing.str("Phone"))) {
                Db.update("UPDATE CUSTOMER SET PasswordSalt = ?, PasswordHash = ? WHERE CustomerID = ?",
                        Db.secret(salt), Db.secret(hash), existing.i("CustomerID"));
                return (long) existing.i("CustomerID");
            }
            return Db.insert("INSERT INTO CUSTOMER (FullName, Email, Phone, DrivingLicenceNo, PasswordSalt, PasswordHash) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", fName, fEmail, fPhone, fLicence, Db.secret(salt), Db.secret(hash));
        });
        User u = new User(Role.CUSTOMER, (int) id, name, email, phone);
        Session.login(u);
        return u;
    }

    /** Accepts 10 digits (optionally with +91 / spaces / dashes) and stores "+91-XXXXXXXXXX" like the sample data. */
    public static String normalisePhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (digits.length() != 10) throw new IllegalArgumentException("Please enter a 10-digit mobile number.");
        return "+91-" + digits;
    }

    // ------------------------------------------------------------------ hashing
    public static String newSalt() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);                  // 16 hex characters
    }

    /** SHA-256(salt + password) as lowercase hex = MySQL SHA2(CONCAT(salt, password), 256). */
    public static String hash(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((salt + password).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void check(String password, String salt, String expected) {
        String actual = hash(salt, password == null ? "" : password);
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII), expected.getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Wrong password. Please try again.");
    }

    private static String clean(String email) { return email == null ? "" : email.trim().toLowerCase(); }
}
