package vrs.ui.customer;

import java.awt.*;
import java.awt.geom.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import javax.swing.*;
import vrs.model.Models.Booking;
import vrs.service.BookingService;
import vrs.service.Session.User;
import vrs.ui.*;
import vrs.ui.comp.*;

/** The customer's own bookings: track status, pay after confirmation, cancel. */
final class MyBookingsPage extends JPanel {
    private final User user;
    private final Seg filter = new Seg("Active", "Completed", "Cancelled", "All");
    private final JPanel list = Ui.transparent(null);
    private final JLabel summary = Ui.label(" ", Theme.font(13), Theme.TEXT_3);
    private List<Booking> bookings = new ArrayList<>();

    MyBookingsPage(User user) {
        super(new BorderLayout());
        this.user = user;
        setOpaque(false);
        Page page = new Page();
        page.setBorder(BorderFactory.createEmptyBorder(26, 32, 32, 32));
        JPanel head = Ui.transparent(new BorderLayout());
        head.add(Ui.col(4, Ui.label("My bookings", Theme.bold(26), Theme.TEXT), summary), BorderLayout.WEST);
        JPanel fw = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        fw.add(filter);
        head.add(fw, BorderLayout.EAST);
        page.add(head, 20);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        page.add(list, 0);
        add(Scroll.of(page));
        filter.onChange(f -> render());
    }

    void reload() {
        list.removeAll();
        list.add(Widgets.loading("Loading your bookings…"));
        list.revalidate();
        Async.run(() -> BookingService.forCustomer(user.id()), b -> { bookings = b; render(); });
    }

    private void render() {
        list.removeAll();
        String f = filter.value();
        int pending = 0, confirmed = 0;
        BigDecimal spent = BigDecimal.ZERO;
        for (Booking b : bookings) {
            if (b.status().equals("Pending")) pending++;
            if (b.status().equals("Confirmed")) confirmed++;
            spent = spent.add(b.paid().subtract(b.refunded()));
        }
        summary.setText(bookings.size() + " bookings   ·   " + pending + " pending   ·   " + confirmed + " confirmed   ·   "
                + Fmt.money(spent) + " paid");
        int shown = 0;
        for (Booking b : bookings) {
            boolean match = switch (f) {
                case "Active" -> b.status().equals("Pending") || b.status().equals("Confirmed");
                case "Completed" -> b.status().equals("Completed");
                case "Cancelled" -> b.status().equals("Cancelled");
                default -> true;
            };
            if (!match) continue;
            JComponent row = new Row(b);
            row.setAlignmentX(0f);
            list.add(row);
            list.add(Box.createVerticalStrut(14));
            shown++;
        }
        if (shown == 0) list.add(Widgets.empty("clipboard", f.equals("Active") ? "No active bookings" : "Nothing here yet",
                f.equals("Active") ? "Go to Explore, pick your dates and book a vehicle." : "Bookings will appear here."));
        list.revalidate();
        list.repaint();
    }

    /** One booking card. */
    private final class Row extends Card {
        Row(Booking b) {
            super(new BorderLayout(20, 0));
            pad(16, 16, 16, 20);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 168));
            setPreferredSize(new Dimension(800, 168));

            JComponent art = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Theme.g2(g);
                    Color c = Theme.typeColor(b.bodyType());
                    g2.setPaint(new GradientPaint(0, 0, Theme.alpha(c, 34), 0, getHeight(), Theme.alpha(c, 8)));
                    g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
                    VehicleIcons.of(b.bodyType(), 150, b.electric()).paintIcon(this, g2, 15, (getHeight() - 80) / 2);
                    g2.dispose();
                }
            };
            art.setPreferredSize(new Dimension(180, 136));
            add(art, BorderLayout.WEST);

            JPanel mid = Ui.transparent(null);
            mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
            JLabel name = Ui.label(b.vehicleName(), Theme.bold(17), Theme.TEXT);
            JLabel reg = Ui.label("Booking #" + b.id() + "   ·   " + b.reg() + "   ·   requested " + Fmt.ago(b.createdAt()), Theme.font(12), Theme.TEXT_3);
            JLabel dates = Ui.label(Fmt.day(b.start()) + "   →   " + Fmt.day(b.end()) + "   ·   " + Fmt.days(b.days()), Theme.bold(13.5f), Theme.TEXT_2);
            dates.setIcon(Icons.of("calendar", 16, Theme.TEXT_3));
            dates.setIconTextGap(8);
            JComponent track = tracker(b);
            for (JComponent c : new JComponent[]{name, reg, dates, track}) c.setAlignmentX(0f);
            mid.add(name);
            mid.add(Box.createVerticalStrut(3));
            mid.add(reg);
            mid.add(Box.createVerticalStrut(12));
            mid.add(dates);
            mid.add(Box.createVerticalStrut(14));
            mid.add(track);
            add(mid, BorderLayout.CENTER);

            JPanel right = Ui.transparent(null);
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
            Badge badge = new Badge(b.status());
            JLabel total = Ui.label(Fmt.money(b.total()), Theme.bold(20), Theme.TEXT);
            String payText = b.status().equals("Cancelled")
                    ? (b.refunded().signum() > 0 ? Fmt.money(b.refunded()) + " refunded" : "No charge")
                    : b.fullyPaid() ? "Fully paid" : b.paid().signum() > 0 ? Fmt.money(b.balance()) + " due" : "Not paid yet";
            JLabel pay = Ui.label(payText, Theme.font(12), b.fullyPaid() && !b.status().equals("Cancelled") ? Theme.ACCENT_HI : Theme.TEXT_3);
            JPanel actions = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            boolean canCancel = (b.status().equals("Pending") || b.status().equals("Confirmed")) && !b.start().isBefore(LocalDate.now());
            if (canCancel) {
                Btn cancel = new Btn("Cancel", Btn.Kind.SECONDARY).small();
                cancel.addActionListener(e -> cancel(b));
                actions.add(cancel);
            }
            if (b.status().equals("Confirmed") && b.balance().signum() > 0) {
                Btn payBtn = new Btn("Pay " + Fmt.money(b.balance()), Btn.Kind.PRIMARY, "wallet").small();
                payBtn.addActionListener(e -> { if (PaymentDialog.open(this, user, b)) reload(); });
                actions.add(payBtn);
            } else if (b.status().equals("Pending")) {
                actions.add(Ui.label("Waiting for confirmation", Theme.font(12), Theme.WARN));
            }
            for (JComponent c : new JComponent[]{badge, total, pay, actions}) c.setAlignmentX(1f);
            right.add(badge);
            right.add(Box.createVerticalStrut(12));
            right.add(total);
            right.add(Box.createVerticalStrut(2));
            right.add(pay);
            right.add(Box.createVerticalGlue());
            right.add(actions);
            right.setPreferredSize(new Dimension(300, 136));
            add(right, BorderLayout.EAST);
        }
    }

    private void cancel(Booking b) {
        String extra = b.paid().signum() > 0 ? "<br><br>" + Fmt.money(b.paid().subtract(b.refunded())) + " will be refunded to your original payment method." : "";
        if (!Modal.confirm(this, "Cancel booking #" + b.id() + "?", b.vehicleName() + ", " + Fmt.day(b.start()) + " → " + Fmt.day(b.end()) + "." + extra,
                "Cancel booking", true)) return;
        Async.act(() -> BookingService.cancelByCustomer(b.id(), user.id()), () -> {
            Toast.success("Booking #" + b.id() + " cancelled.");
            reload();
        });
    }

    /** Requested → Confirmed → Paid → Completed progress line. */
    private static JComponent tracker(Booking b) {
        String[] steps = {"Requested", "Confirmed", "Paid", "Completed"};
        boolean cancelled = b.status().equals("Cancelled");
        int reached = switch (b.status()) {
            case "Confirmed" -> b.fullyPaid() ? 2 : 1;
            case "Completed" -> 3;
            default -> 0;
        };
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                int n = steps.length, w = Math.min(getWidth(), 460), gap = (w - 20) / (n - 1);
                for (int i = 0; i < n; i++) {
                    int x = 8 + i * gap;
                    boolean on = i <= reached && !cancelled;
                    if (i < n - 1) {
                        g2.setColor(i < reached && !cancelled ? Theme.ACCENT : Theme.BORDER_HI);
                        g2.setStroke(new BasicStroke(2f));
                        g2.draw(new Line2D.Double(x + 8, 8, x + gap - 8, 8));
                    }
                    Color dot = cancelled && i == 0 ? Theme.DANGER : on ? Theme.ACCENT : Theme.SURFACE_3;
                    g2.setColor(dot);
                    g2.fill(new Ellipse2D.Double(x - 6, 2, 12, 12));
                    if (on && i == reached) {
                        g2.setColor(Theme.alpha(Theme.ACCENT, 60));
                        g2.setStroke(new BasicStroke(3f));
                        g2.draw(new Ellipse2D.Double(x - 9, -1, 18, 18));
                    }
                    g2.setFont(Theme.font(11.5f));
                    g2.setColor(on ? Theme.TEXT_2 : Theme.TEXT_3);
                    String s = cancelled && i == 0 ? "Cancelled" : steps[i];
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = i == 0 ? x - 6 : i == n - 1 ? x + 6 - fm.stringWidth(s) : x - fm.stringWidth(s) / 2;
                    g2.drawString(s, tx, 32);
                }
                g2.dispose();
            }
        };
        c.setPreferredSize(new Dimension(470, 38));
        c.setMaximumSize(new Dimension(470, 38));
        return c;
    }
}
