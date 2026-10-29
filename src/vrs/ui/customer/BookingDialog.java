package vrs.ui.customer;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import javax.swing.*;
import vrs.db.DbErrors;
import vrs.model.Models.*;
import vrs.service.BookingService;
import vrs.service.Session.User;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Review the price and send a booking request (status Pending). */
final class BookingDialog {
    private BookingDialog() {}

    static boolean open(Component parent, User user, Offer o, LocalDate from, LocalDate to) {
        Vehicle v = o.vehicle();
        Modal m = new Modal(parent, "Request this " + v.bodyType().toLowerCase(), "Review the details before sending your request.", 520);
        boolean[] done = {false};

        JComponent head = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                Color c = Theme.typeColor(v.bodyType());
                g2.setColor(Theme.alpha(c, 26));
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 14, 14));
                VehicleIcons.of(v.bodyType(), 150, v.electric()).paintIcon(this, g2, 14, (getHeight() - 80) / 2);
                g2.setFont(Theme.bold(18));
                g2.setColor(Theme.TEXT);
                g2.drawString(v.name(), 186, 40);
                g2.setFont(Theme.mono(12));
                g2.setColor(Theme.TEXT_3);
                g2.drawString(v.reg(), 186, 60);
                g2.setFont(Theme.font(12.5f));
                g2.setColor(Theme.TEXT_2);
                g2.drawString(v.fuel() + "  ·  " + v.transmission() + "  ·  " + v.seats() + " seats", 186, 82);
                g2.dispose();
            }
        };
        head.setPreferredSize(new Dimension(460, 108));
        head.setAlignmentX(0f);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
        m.body().add(head);
        m.body().add(Box.createVerticalStrut(16));

        JPanel dates = Ui.transparent(new GridLayout(1, 2, 12, 0));
        dates.add(info("Pick-up", Fmt.day(from) + " " + from.getYear()));
        dates.add(info("Return", Fmt.day(to) + " " + to.getYear()));
        dates.setAlignmentX(0f);
        dates.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        m.body().add(dates);
        m.body().add(Box.createVerticalStrut(16));

        JPanel bill = Ui.transparent(new GridLayout(0, 2, 0, 8));
        line(bill, "Daily rate (" + Fmt.days(o.days()) + ")", Fmt.money(o.dailyRate()) + " × " + o.days(), Theme.TEXT_2);
        line(bill, "Subtotal", Fmt.money(o.subtotal()), Theme.TEXT_2);
        if (o.discountPercent().signum() > 0)
            line(bill, "Long-rental discount (" + Fmt.percent(o.discountPercent()) + ")", "- " + Fmt.money(o.saving()), Theme.ACCENT_HI);
        bill.setAlignmentX(0f);
        m.body().add(bill);
        m.body().add(Box.createVerticalStrut(10));
        m.body().add(Ui.divider());
        m.body().add(Box.createVerticalStrut(8));
        JPanel tot = Ui.transparent(new BorderLayout());
        tot.add(Ui.label("Total", Theme.bold(15), Theme.TEXT), BorderLayout.WEST);
        tot.add(Ui.label(Fmt.money(o.total()), Theme.bold(22), Theme.TEXT), BorderLayout.EAST);
        tot.setAlignmentX(0f);
        tot.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        m.body().add(tot);
        m.body().add(Box.createVerticalStrut(14));

        JLabel note = new JLabel("<html><body style='width:420px'>Your request is <b>Pending</b> until our team confirms it. "
                + "The vehicle is held for you meanwhile. Payment opens after confirmation.</body></html>");
        note.setFont(Theme.font(12.5f));
        note.setForeground(Theme.TEXT_2);
        note.setIcon(Icons.of("info", 16, Theme.INFO));
        note.setIconTextGap(10);
        m.body().add(Ui.left(note));

        m.addCancel("Cancel");
        m.addButton("Send request", Btn.Kind.PRIMARY, () -> {
            m.busy(true, "Sending…");
            Async.run(() -> BookingService.request(user.id(), v.id(), from, to), id -> {
                done[0] = true;
                m.dispose();
                Toast.success("Booking request #" + id + " sent for " + v.name() + ".");
            }, e -> { m.busy(false, "Send request"); m.showError(DbErrors.friendly(e)); });
        });
        m.open();
        return done[0];
    }

    private static JComponent info(String label, String value) {
        Card c = new Card(new BorderLayout()).fill(Theme.SURFACE_2).radius(12).pad(10, 14, 10, 14);
        c.add(Ui.col(3, Ui.label(label.toUpperCase(), Theme.bold(10.5f), Theme.TEXT_3), Ui.label(value, Theme.bold(14), Theme.TEXT)));
        return c;
    }

    private static void line(JPanel p, String k, String v, Color vc) {
        p.add(Ui.label(k, Theme.font(13), Theme.TEXT_2));
        JLabel r = Ui.label(v, Theme.font(13), vc);
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        p.add(r);
    }
}
