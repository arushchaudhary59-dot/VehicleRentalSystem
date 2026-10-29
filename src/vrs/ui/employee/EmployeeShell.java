package vrs.ui.employee;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import javax.swing.*;
import vrs.App;
import vrs.service.Session.User;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Employee area: left sidebar + pages. */
public final class EmployeeShell extends JPanel {
    /** Every employee page can reload its data when it is shown. */
    interface Refreshable { void refresh(); }

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, JComponent> pages = new LinkedHashMap<>();
    private final List<NavItem> nav = new ArrayList<>();
    final User user;

    public EmployeeShell(User user) {
        super(new BorderLayout());
        this.user = user;
        setBackground(Theme.BG);
        content.setOpaque(false);
        page("Dashboard", "grid", new DashboardPage(this));
        page("Bookings", "clipboard", new BookingsPage(this));
        page("Payments", "card", new PaymentsPage(this));
        page("Vehicles", "car", new VehiclesPage(this));
        page("Query Log", "terminal", new QueryLogPage(this));
        page("ER Diagram", "diagram", new ErDiagramPage(this));
        if ("Manager".equals(user.detail())) page("Staff", "user", new StaffPage(this));   // managers only
        add(sidebar(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        show("Dashboard");
    }

    private void page(String name, String icon, JComponent p) {
        pages.put(name, p);
        content.add(p, name);
        nav.add(new NavItem(name, icon));
    }

    private String current = "Dashboard";
    public String currentPage() { return current; }

    public void show(String name) {
        if (!pages.containsKey(name)) name = "Dashboard";
        current = name;
        cards.show(content, name);
        for (NavItem n : nav) { n.active = n.name.equals(name); n.repaint(); }
        if (pages.get(name) instanceof Refreshable r) r.refresh();
    }

    /** Opens the bookings page filtered on one status. */
    void showBookings(String status) {
        ((BookingsPage) pages.get("Bookings")).preset(status);
        show("Bookings");
    }

    void setPendingCount(int n) {
        for (NavItem i : nav) if (i.name.equals("Bookings")) { i.count = n; i.repaint(); }
    }

    private JComponent sidebar() {
        JPanel side = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.SURFACE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.BORDER);
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
            }
        };
        side.setPreferredSize(new Dimension(236, 10));
        side.setBorder(BorderFactory.createEmptyBorder(20 + App.titleBarInset(), 14, 16, 14));

        JPanel top = Ui.transparent(null);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JComponent logo = brand();
        top.add(logo);
        top.add(Box.createVerticalStrut(22));
        JLabel sec = Ui.label("WORKSPACE", Theme.bold(10.5f), Theme.TEXT_3);
        sec.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 0));
        top.add(Ui.left(sec));
        for (NavItem n : nav) { n.setAlignmentX(0f); top.add(n); top.add(Box.createVerticalStrut(2)); }
        side.add(top, BorderLayout.NORTH);

        Card me = new Card(new BorderLayout(10, 0)).fill(Theme.SURFACE_2).radius(12).pad(10, 10, 10, 6);
        me.add(Widgets.avatar(user.initials(), 34, Theme.INFO), BorderLayout.WEST);
        me.add(Ui.col(1, Ui.label(user.name(), Theme.bold(13), Theme.TEXT), Ui.label(user.detail(), Theme.font(11.5f), Theme.TEXT_3)), BorderLayout.CENTER);
        Btn out = new Btn("", Btn.Kind.GHOST, "logout").small().tip("Log out");
        out.addActionListener(e -> App.logout());
        JPanel ow = Ui.transparent(new GridBagLayout());
        ow.add(ThemeToggle.create());
        ow.add(out);
        me.add(ow, BorderLayout.EAST);
        side.add(me, BorderLayout.SOUTH);
        return side;
    }

    private static JComponent brand() {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                g2.setColor(Theme.ACCENT);
                g2.fill(new RoundRectangle2D.Double(8, 2, 36, 36, 10, 10));
                Icons.of("road", 22, Theme.ACCENT_INK).paintIcon(this, g2, 15, 9);
                g2.setFont(Theme.bold(17));
                g2.setColor(Theme.TEXT);
                g2.drawString("VRS Console", 54, 19);
                g2.setFont(Theme.font(11));
                g2.setColor(Theme.TEXT_3);
                g2.drawString("Employee workspace", 54, 34);
                g2.dispose();
            }
        };
        c.setPreferredSize(new Dimension(200, 40));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        c.setAlignmentX(0f);
        return c;
    }

    private final class NavItem extends JComponent {
        final String name, icon;
        boolean active, over;
        int count;

        NavItem(String name, String icon) {
            this.name = name;
            this.icon = icon;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(200, 42));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { EmployeeShell.this.show(name); }
                public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                public void mouseExited(MouseEvent e) { over = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            int w = getWidth(), h = getHeight();
            if (active || over) {
                g2.setColor(active ? Theme.alpha(Theme.ACCENT, 26) : Theme.SURFACE_2);
                g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 10, 10));
            }
            if (active) {
                g2.setColor(Theme.ACCENT);
                g2.fill(new RoundRectangle2D.Double(0, 10, 3, h - 20, 3, 3));
            }
            Icons.of(icon, 18, active ? Theme.ACCENT_HI : over ? Theme.TEXT : Theme.TEXT_2).paintIcon(this, g2, 14, (h - 18) / 2);
            g2.setFont(active ? Theme.bold(13.5f) : Theme.font(13.5f));
            g2.setColor(active || over ? Theme.TEXT : Theme.TEXT_2);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(name, 44, (h - fm.getHeight()) / 2 + fm.getAscent());
            if (count > 0) {
                String s = String.valueOf(count);
                g2.setFont(Theme.bold(11));
                int bw = Math.max(22, g2.getFontMetrics().stringWidth(s) + 12);
                g2.setColor(Theme.WARN);
                g2.fill(new RoundRectangle2D.Double(w - bw - 10, (h - 20) / 2.0, bw, 20, 20, 20));
                g2.setColor(Theme.ACCENT_INK);
                g2.drawString(s, w - bw - 10 + (bw - g2.getFontMetrics().stringWidth(s)) / 2, h / 2 + 4);
            }
            g2.dispose();
        }
    }

    /** Title + subtitle on the left, action buttons on the right. */
    static JComponent header(String title, String subtitle, JComponent... actions) {
        JPanel p = Ui.transparent(new BorderLayout(20, 0));
        p.add(Ui.col(4, Ui.label(title, Theme.bold(24), Theme.TEXT), Ui.label(subtitle, Theme.font(13.5f), Theme.TEXT_3)), BorderLayout.WEST);
        JPanel a = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        for (JComponent c : actions) a.add(c);
        p.add(a, BorderLayout.EAST);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /** Standard page padding (leaves room for the macOS title bar). */
    static javax.swing.border.Border pagePad() { return BorderFactory.createEmptyBorder(24 + App.titleBarInset(), 28, 24, 28); }
}
