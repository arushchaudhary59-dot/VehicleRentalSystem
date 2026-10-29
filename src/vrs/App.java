package vrs;

import java.awt.*;
import javax.swing.*;
import vrs.service.Session;
import vrs.ui.Theme;
import vrs.ui.auth.AuthScreen;
import vrs.ui.customer.CustomerShell;
import vrs.ui.employee.EmployeeShell;

/**
 * Vehicle Rental System - desktop client (Java Swing + JDBC + MySQL).
 *
 * Run this class. Before the first run:
 *   1. import sql/vehicle_rental_system.sql into MySQL
 *   2. make sure the MySQL Connector/J jar is added as a library
 * The app asks for the MySQL password the first time and saves it in db.properties.
 */
public final class App {
    private App() {}

    public static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
    private static JFrame frame;

    public static void main(String[] args) {
        // must be set before any AWT class is used
        boolean light = java.util.prefs.Preferences.userRoot().node("vrs-vehicle-rental").getBoolean("lightMode", false);
        System.setProperty("apple.awt.application.appearance", light ? "NSAppearanceNameAqua" : "NSAppearanceNameDarkAqua");
        System.setProperty("apple.awt.application.name", "Vehicle Rental System");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        Theme.install();
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Vehicle Rental System");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.getContentPane().setBackground(Theme.BG);
            if (MAC) {
                // let our dark UI run under the macOS title bar
                frame.getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
                frame.getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
                frame.getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
            }
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(Math.min(1360, screen.width - 60), Math.min(860, screen.height - 60));
            frame.setMinimumSize(new Dimension(1100, 700));
            frame.setLocationRelativeTo(null);
            showAuth();
            frame.setVisible(true);
        });
    }

    public static JFrame frame() { return frame; }

    /** Extra space at the top of the window for the macOS traffic-light buttons. */
    public static int titleBarInset() { return MAC ? 28 : 0; }

    public static void showAuth() { swap(new AuthScreen()); }

    public static void showHome() {
        Session.User u = Session.current();
        if (u == null) showAuth();
        else if (u.role() == Session.Role.CUSTOMER) swap(new CustomerShell(u));
        else swap(new EmployeeShell(u));
    }

    /** Switches dark / light mode and rebuilds the current screen on the same page. */
    public static void toggleTheme() {
        Theme.setLight(!Theme.isLight());
        Theme.install();
        frame.getContentPane().setBackground(Theme.BG);
        if (MAC) frame.getRootPane().putClientProperty("apple.awt.windowAppearance",
                Theme.isLight() ? "NSAppearanceNameAqua" : "NSAppearanceNameDarkAqua");
        Container cur = frame.getContentPane();
        Session.User u = Session.current();
        if (u == null) showAuth();
        else if (cur instanceof CustomerShell cs) { String p = cs.currentPage(); CustomerShell n = new CustomerShell(u); swap(n); n.show(p); }
        else if (cur instanceof EmployeeShell es) { String p = es.currentPage(); EmployeeShell n = new EmployeeShell(u); swap(n); n.show(p); }
        else showHome();
    }

    public static void logout() {
        Session.logout();
        showAuth();
    }

    private static void swap(JComponent c) {
        frame.setContentPane(c);
        frame.revalidate();
        frame.repaint();
    }
}
