package vrs.ui.customer;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;
import vrs.db.DbErrors;
import vrs.model.Models.Booking;
import vrs.service.PaymentService;
import vrs.service.Session.User;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Simulated checkout: choose UPI / Card / Net Banking and pay the balance of a confirmed booking. */
final class PaymentDialog {
    private PaymentDialog() {}

    static boolean open(Component parent, User user, Booking b) {
        Modal m = new Modal(parent, "Pay for booking #" + b.id(), b.vehicleName() + "  ·  " + Fmt.day(b.start()) + " → " + Fmt.day(b.end()), 500);
        boolean[] paid = {false};
        String[] method = {"UPI"};

        JPanel amount = new Card(new BorderLayout()).fill(Theme.SURFACE_2).radius(14).pad(16, 18, 16, 18);
        amount.add(Ui.col(4, Ui.label("AMOUNT DUE", Theme.bold(11), Theme.TEXT_3), Ui.label(Fmt.money(b.balance()), Theme.bold(30), Theme.TEXT)), BorderLayout.WEST);
        amount.add(Ui.col(4, Ui.label("Total " + Fmt.money(b.total()), Theme.font(12.5f), Theme.TEXT_2),
                Ui.label("Paid " + Fmt.money(b.paid()), Theme.font(12.5f), Theme.TEXT_3)), BorderLayout.EAST);
        amount.setAlignmentX(0f);
        amount.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        m.body().add(amount);
        m.body().add(Box.createVerticalStrut(18));
        m.body().add(Ui.left(Ui.label("Payment method", Theme.bold(12), Theme.TEXT_2)));
        m.body().add(Box.createVerticalStrut(8));

        JPanel tiles = Ui.transparent(new GridLayout(1, 3, 10, 0));
        Tile[] all = {new Tile("UPI", "phone", "Any UPI app"), new Tile("Card", "card", "Debit / credit"), new Tile("Net Banking", "wallet", "All banks")};
        for (Tile t : all) {
            t.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { method[0] = t.method; for (Tile x : all) x.sel = x == t; for (Tile x : all) x.repaint(); }
            });
            tiles.add(t);
        }
        all[0].sel = true;
        tiles.setAlignmentX(0f);
        tiles.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        m.body().add(tiles);
        m.body().add(Box.createVerticalStrut(14));
        JLabel note = Ui.label("<html><body style='width:420px'>Demo checkout: no real money is charged and no card details are asked for. "
                + "A transaction is recorded in the PAYMENT table.</body></html>", Theme.font(12), Theme.TEXT_3);
        note.setIcon(Icons.of("lock", 14, Theme.TEXT_3));
        note.setIconTextGap(8);
        m.body().add(Ui.left(note));

        m.addCancel("Not now");
        m.addButton("Pay " + Fmt.money(b.balance()), Btn.Kind.PRIMARY, () -> {
            m.busy(true, "Processing…");
            Timer delay = new Timer(1100, e -> Async.run(() -> PaymentService.payBalance(b.id(), user.id(), method[0]), ref -> {
                paid[0] = true;
                m.dispose();
                Toast.success("Payment successful" + (ref.isEmpty() ? "" : "  ·  Ref " + ref));
            }, err -> { m.busy(false, "Pay " + Fmt.money(b.balance())); m.showError(DbErrors.friendly(err)); }));
            delay.setRepeats(false);
            delay.start();
        });
        m.open();
        return paid[0];
    }

    private static final class Tile extends JComponent {
        final String method, icon, sub;
        boolean sel, over;

        Tile(String method, String icon, String sub) {
            this.method = method; this.icon = icon; this.sub = sub;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(140, 92));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                public void mouseExited(MouseEvent e) { over = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(sel ? Theme.alpha(Theme.ACCENT, 30) : over ? Theme.SURFACE_3 : Theme.SURFACE_2);
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 14, 14));
            g2.setColor(sel ? Theme.ACCENT : Theme.BORDER);
            g2.setStroke(new BasicStroke(sel ? 1.6f : 1f));
            g2.draw(new RoundRectangle2D.Double(0.8, 0.8, w - 1.6, h - 1.6, 14, 14));
            Icons.of(icon, 22, sel ? Theme.ACCENT_HI : Theme.TEXT_2).paintIcon(this, g2, 14, 14);
            if (sel) {
                g2.setColor(Theme.ACCENT);
                g2.fill(new Ellipse2D.Double(w - 26, 12, 14, 14));
                Icons.of("check", 10, Theme.ACCENT_INK).paintIcon(this, g2, w - 24, 14);
            }
            g2.setFont(Theme.bold(14));
            g2.setColor(Theme.TEXT);
            g2.drawString(method, 14, h - 30);
            g2.setFont(Theme.font(11.5f));
            g2.setColor(Theme.TEXT_3);
            g2.drawString(sub, 14, h - 13);
            g2.dispose();
        }
    }
}
