package vrs.ui.customer;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import vrs.App;
import vrs.service.Session.User;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Customer area: top navigation bar + Explore / My bookings pages. */
public final class CustomerShell extends JPanel {
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final ExplorePage explore;
    private final MyBookingsPage bookings;
    private final NavTab exploreTab, bookingsTab;

    public CustomerShell(User user) {
        super(new BorderLayout());
        setBackground(Theme.BG);
        explore = new ExplorePage(user, this);
        bookings = new MyBookingsPage(user);
        content.setOpaque(false);
        content.add(explore, "explore");
        content.add(bookings, "bookings");

        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.SURFACE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.BORDER);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        bar.setBorder(BorderFactory.createEmptyBorder(App.titleBarInset(), App.MAC ? 84 : 24, 0, 24));
        bar.setPreferredSize(new Dimension(10, 66 + App.titleBarInset()));

        JPanel left = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(logo());
        left.add(Box.createHorizontalStrut(34));
        exploreTab = new NavTab("Explore", "search", () -> show("explore"));
        bookingsTab = new NavTab("My bookings", "clipboard", () -> show("bookings"));
        left.add(exploreTab);
        left.add(bookingsTab);
        bar.add(left, BorderLayout.WEST);

        JPanel right = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 13));
        right.add(Widgets.avatar(user.initials(), 34, Theme.ACCENT));
        JPanel who = Ui.col(1, Ui.label(user.name(), Theme.bold(13), Theme.TEXT), Ui.label(user.email(), Theme.font(11.5f), Theme.TEXT_3));
        right.add(who);
        Btn logout = new Btn("", Btn.Kind.GHOST, "logout").small().tip("Log out");
        logout.addActionListener(e -> App.logout());
        right.add(ThemeToggle.create());
        right.add(logout);
        bar.add(right, BorderLayout.EAST);

        add(bar, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        show("explore");
    }

    private String current = "explore";
    public String currentPage() { return current; }

    public void show(String page) {
        current = page;
        cards.show(content, page);
        exploreTab.active = page.equals("explore");
        bookingsTab.active = page.equals("bookings");
        exploreTab.repaint();
        bookingsTab.repaint();
        if (page.equals("bookings")) bookings.reload();
        else explore.refresh();
    }

    static JComponent logo() {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                int y = (getHeight() - 34) / 2;
                g2.setColor(Theme.ACCENT);
                g2.fill(new RoundRectangle2D.Double(0, y, 34, 34, 10, 10));
                Icons.of("road", 20, Theme.ACCENT_INK).paintIcon(this, g2, 7, y + 7);
                g2.setFont(Theme.bold(17));
                g2.setColor(Theme.TEXT);
                g2.drawString("VRS", 44, y + 16);
                g2.setFont(Theme.font(10.5f));
                g2.setColor(Theme.TEXT_3);
                g2.drawString("Vehicle Rental", 44, y + 30);
                g2.dispose();
            }
        };
        c.setPreferredSize(new Dimension(130, 66));
        return c;
    }

    /** Top-bar tab with an animated underline. */
    private static final class NavTab extends JComponent {
        private final String text, icon;
        boolean active, over;

        NavTab(String text, String icon, Runnable onClick) {
            this.text = text;
            this.icon = icon;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(getFontMetrics(Theme.bold(14)).stringWidth(text) + 58, 66));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { onClick.run(); }
                public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                public void mouseExited(MouseEvent e) { over = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            Color c = active ? Theme.TEXT : over ? Theme.TEXT : Theme.TEXT_2;
            Icons.of(icon, 17, active ? Theme.ACCENT_HI : c).paintIcon(this, g2, 14, (getHeight() - 17) / 2);
            g2.setFont(active ? Theme.bold(14) : Theme.font(14));
            g2.setColor(c);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 38, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            if (active) {
                g2.setColor(Theme.ACCENT);
                g2.fill(new RoundRectangle2D.Double(12, getHeight() - 3, getWidth() - 24, 3, 3, 3));
            }
            g2.dispose();
        }
    }
}
