package vrs.ui.auth;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;
import vrs.App;
import vrs.db.*;
import vrs.service.AuthService;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Login / sign-up screen for both roles. */
public final class AuthScreen extends JPanel {
    private final Seg role = new Seg("Customer", "Employee");
    private boolean signUp;
    private final JPanel form = Ui.transparent(null);
    private final JLabel title = Ui.label("", Theme.bold(26), Theme.TEXT);
    private final JLabel subtitle = Ui.label("", Theme.font(14), Theme.TEXT_2);
    private final JLabel error = Ui.label(" ", Theme.font(12.5f), Theme.DANGER);
    private final JLabel dbStatus = Ui.label("Checking database…", Theme.font(12), Theme.TEXT_3);
    private final Btn submit = new Btn("Log in", Btn.Kind.PRIMARY, "arrowRight");
    private final JLabel switchLink = Ui.label("", Theme.font(13), Theme.TEXT_2);

    private final JTextField name = Input.text("Full name", "user");
    private final JTextField email = Input.text("E-mail address", "mail");
    private final JTextField phone = Input.text("Mobile number", "phone");
    private final JTextField licence = Input.text("Licence (optional)", "id");
    private final JPasswordField password = Input.password("Password");
    private final JPasswordField confirm = Input.password("Confirm");

    public AuthScreen() {
        super(new BorderLayout());
        setBackground(Theme.BG);
        add(new Hero(), BorderLayout.WEST);

        JPanel right = Ui.transparent(new GridBagLayout());
        Card card = new Card(new BorderLayout()).radius(22).pad(30, 32, 26, 32);
        card.setPreferredSize(new Dimension(440, 600));
        JPanel col = Ui.transparent(null);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        role.setAlignmentX(0f);
        role.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        col.add(role);
        col.add(Box.createVerticalStrut(24));
        title.setAlignmentX(0f);
        subtitle.setAlignmentX(0f);
        col.add(title);
        col.add(Box.createVerticalStrut(6));
        col.add(subtitle);
        col.add(Box.createVerticalStrut(22));
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setAlignmentX(0f);
        col.add(form);
        error.setAlignmentX(0f);
        col.add(error);
        col.add(Box.createVerticalStrut(10));
        submit.setAlignmentX(0f);
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        col.add(submit);
        col.add(Box.createVerticalStrut(18));
        switchLink.setAlignmentX(0f);
        switchLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        col.add(switchLink);
        card.add(col, BorderLayout.CENTER);

        JPanel db = Ui.transparent(new BorderLayout(8, 0));
        db.add(dbStatus, BorderLayout.CENTER);
        Btn settings = new Btn("", Btn.Kind.GHOST, "db").small().tip("Database settings");
        settings.addActionListener(e -> { if (DbSettingsDialog.open(this)) checkDb(); });
        JPanel tools = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 2, 0));
        tools.add(ThemeToggle.create());
        tools.add(settings);
        db.add(tools, BorderLayout.EAST);
        card.add(db, BorderLayout.SOUTH);
        right.add(card);
        add(right, BorderLayout.CENTER);

        role.onChange(r -> { signUp = false; rebuild(); });
        submit.addActionListener(e -> submit());
        switchLink.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { if (role.index() == 0) { signUp = !signUp; rebuild(); } }
        });
        for (JTextField f : new JTextField[]{name, email, phone, licence, password, confirm})
            f.addActionListener(e -> submit());
        rebuild();
        checkDb();
    }

    private void rebuild() {
        boolean customer = role.index() == 0;
        form.removeAll();
        error.setText(" ");
        if (signUp) {
            title.setText("Create your account");
            subtitle.setText("Takes a minute. Book cars, bikes and scooters.");
            add(form, name);
            add(form, email);
            form.add(pair(phone, licence));
            form.add(Box.createVerticalStrut(12));
            form.add(pair(password, confirm));
            form.add(Box.createVerticalStrut(6));
            submit.setText("Create account");
            switchLink.setText("<html>Already have an account? <b><font color='" + Theme.hex(Theme.ACCENT_HI) + "'>Log in</font></b></html>");
        } else {
            title.setText(customer ? "Welcome back" : "Staff sign in");
            subtitle.setText(customer ? "Log in to find and book your next ride." : "Manage bookings, payments and the fleet.");
            add(form, email);
            add(form, password);
            submit.setText("Log in");
            switchLink.setText(customer
                    ? "<html>New here? <b><font color='" + Theme.hex(Theme.ACCENT_HI) + "'>Create an account</font></b></html>"
                    : "<html><font color='" + Theme.hex(Theme.TEXT_3) + "'>Employee accounts are created by the administrator.</font></html>");
        }
        form.revalidate();
        form.repaint();
        SwingUtilities.invokeLater(() -> (signUp ? name : email).requestFocusInWindow());
    }

    private static void add(JPanel p, JComponent f) {
        f.setAlignmentX(0f);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        p.add(f);
        p.add(Box.createVerticalStrut(12));
    }

    private static JPanel pair(JComponent a, JComponent b) {
        JPanel p = Ui.transparent(new GridLayout(1, 2, 10, 0));
        p.add(a);
        p.add(b);
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return p;
    }

    private void submit() {
        if (!submit.isEnabled()) return;
        boolean customer = role.index() == 0;
        String em = email.getText(), pw = new String(password.getPassword());
        if (em.isBlank()) { fail("Enter your e-mail.", email); return; }
        if (pw.isEmpty()) { fail("Enter your password.", password); return; }
        if (signUp && !pw.equals(new String(confirm.getPassword()))) { fail("Passwords do not match.", confirm); return; }
        submit.setEnabled(false);
        String label = submit.getText();
        submit.setText(signUp ? "Creating account…" : "Signing in…");
        String nm = name.getText(), ph = phone.getText(), lic = licence.getText();
        boolean su = signUp;
        Async.run(() -> su ? AuthService.signUp(nm, em, ph, lic, pw)
                        : customer ? AuthService.loginCustomer(em, pw) : AuthService.loginEmployee(em, pw),
                user -> {
                    if (su) Toast.success("Welcome, " + user.firstName() + "! Your account is ready.");
                    App.showHome();
                },
                e -> {
                    submit.setEnabled(true);
                    submit.setText(label);
                    fail(DbErrors.friendly(e), null);
                });
    }

    private void fail(String msg, JComponent field) {
        error.setText("<html><body style='width:360px'>" + msg + "</body></html>");
        if (field != null) { Input.error(field, true); field.requestFocusInWindow(); }
    }

    private void checkDb() {
        dbStatus.setText("Checking database…");
        dbStatus.setIcon(dot(Theme.WARN));
        DbConfig c = Db.config();
        Async.run(() -> Db.test(c), v -> {
            dbStatus.setText("MySQL " + v + "  ·  " + c.database + " @ " + c.host);
            dbStatus.setIcon(dot(Theme.ACCENT));
        }, e -> {
            dbStatus.setText("<html><font color='" + Theme.hex(Theme.DANGER) + "'>Not connected</font> — " + DbErrors.friendly(e) + "</html>");
            dbStatus.setIcon(dot(Theme.DANGER));
            if (!java.nio.file.Files.exists(DbConfig.FILE)) {
                SwingUtilities.invokeLater(() -> { if (DbSettingsDialog.open(this)) checkDb(); });
            }
        });
    }

    private static Icon dot(Color c) {
        return new Icon() {
            public int getIconWidth() { return 10; }
            public int getIconHeight() { return 10; }
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = Theme.g2(g);
                g2.setColor(Theme.alpha(c, 60));
                g2.fill(new Ellipse2D.Double(x, y, 10, 10));
                g2.setColor(c);
                g2.fill(new Ellipse2D.Double(x + 2.5, y + 2.5, 5, 5));
                g2.dispose();
            }
        };
    }

    /** Left half: brand, selling points and an animated road with vehicles. */
    private static final class Hero extends JComponent {
        private double offset;
        private final Timer timer = new Timer(33, e -> { offset += 1.6; repaint(); });
        private static final String[] TYPES = {"Hatchback", "Cruiser", "SUV", "Scooter", "Sedan", "Sports", "MUV", "Commuter"};

        Hero() { setPreferredSize(new Dimension(560, 700)); }
        @Override public void addNotify() { super.addNotify(); timer.start(); }
        @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            int w = getWidth(), h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.BG, Theme.ACCENT, 0.08), w, h, Theme.BG));
            g2.fillRect(0, 0, w, h);
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.15, h * 0.1), w * 0.8f,
                    new float[]{0f, 1f}, new Color[]{Theme.alpha(Theme.ACCENT, 46), Theme.alpha(Theme.ACCENT, 0)}));
            g2.fillRect(0, 0, w, h);
            g2.setColor(Theme.alpha(Theme.TEXT, 9));                       // grid
            for (int x = 0; x < w; x += 32) g2.drawLine(x, 0, x, h);
            for (int y = 0; y < h; y += 32) g2.drawLine(0, y, w, y);
            g2.setColor(Theme.BORDER);
            g2.drawLine(w - 1, 0, w - 1, h);

            int x0 = 56, y = 70 + App.titleBarInset();
            g2.setColor(Theme.ACCENT);
            g2.fill(new RoundRectangle2D.Double(x0, y, 44, 44, 12, 12));
            Icons.of("road", 26, Theme.ACCENT_INK).paintIcon(this, g2, x0 + 9, y + 9);
            g2.setFont(Theme.bold(20));
            g2.setColor(Theme.TEXT);
            g2.drawString("VRS", x0 + 58, y + 21);
            g2.setFont(Theme.font(12.5f));
            g2.setColor(Theme.TEXT_2);
            g2.drawString("Vehicle Rental System", x0 + 58, y + 39);

            y += 120;
            g2.setFont(Theme.bold(38));
            g2.setColor(Theme.TEXT);
            g2.drawString("Your ride,", x0, y);
            g2.setColor(Theme.ACCENT_HI);
            g2.drawString("on your dates.", x0, y + 48);
            g2.setFont(Theme.font(15));
            g2.setColor(Theme.TEXT_2);
            g2.drawString("Cars, bikes and scooters with live availability", x0, y + 92);
            g2.drawString("and clear, duration-based pricing.", x0, y + 114);

            String[][] points = {{"calendar", "Real-time availability — no double bookings"},
                    {"trend", "Longer rentals cost less per day"},
                    {"lock", "Salted SHA-256 password storage"}};
            int py = y + 160;
            for (String[] p : points) {
                g2.setColor(Theme.alpha(Theme.ACCENT, 34));
                g2.fill(new RoundRectangle2D.Double(x0, py - 20, 30, 30, 9, 9));
                Icons.of(p[0], 16, Theme.ACCENT_HI).paintIcon(this, g2, x0 + 7, py - 13);
                g2.setFont(Theme.font(14));
                g2.setColor(Theme.TEXT);
                g2.drawString(p[1], x0 + 44, py);
                py += 44;
            }

            int roadY = h - 120;                                            // animated road
            g2.setColor(Theme.SURFACE_2);
            g2.fillRect(0, roadY, w, 70);
            g2.setColor(Theme.alpha(Theme.ACCENT, 70));
            g2.fillRect(0, roadY, w, 2);
            g2.setColor(Theme.alpha(Theme.TEXT, 40));
            for (double x = -(offset * 2 % 60); x < w; x += 60) g2.fill(new RoundRectangle2D.Double(x, roadY + 52, 30, 3, 2, 2));
            double spacing = 150, span = spacing * TYPES.length;
            for (int i = 0; i < TYPES.length; i++) {
                double x = ((i * spacing + offset) % span) - 120;
                if (x > w) continue;
                VehicleIcons.of(TYPES[i], 92, i == 3).paintIcon(this, g2, (int) x, roadY - 16);
            }
            g2.setFont(Theme.font(11.5f));
            g2.setColor(Theme.TEXT_3);
            g2.drawString("DBMS Project  ·  Java Swing + JDBC + MySQL", x0, h - 22);
            g2.dispose();
        }
    }
}
