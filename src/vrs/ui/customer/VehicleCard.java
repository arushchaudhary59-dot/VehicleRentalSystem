package vrs.ui.customer;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import vrs.model.Models.*;
import vrs.ui.*;
import vrs.ui.comp.*;

/** One result in the Explore grid. */
final class VehicleCard extends Card {
    private final Offer offer;

    VehicleCard(Offer offer, Runnable onBook) {
        super(new BorderLayout());
        this.offer = offer;
        Vehicle v = offer.vehicle();
        pad(12, 12, 16, 16);

        JComponent art = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                int w = getWidth(), h = getHeight();
                Color c = Theme.typeColor(v.bodyType());
                g2.setPaint(new GradientPaint(0, 0, Theme.alpha(c, 34), 0, h, Theme.alpha(c, 8)));
                g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 12, 12));
                float lift = hoverAmount() * 4;
                int iw = Math.min(190, w - 40);
                VehicleIcons.of(v.bodyType(), iw, v.electric()).paintIcon(this, g2, (w - iw) / 2, (int) ((h - iw * 64 / 120) / 2 + 8 - lift));
                Badge.paint(g2, v.bodyType(), c, 10, 10, Theme.bold(11));
                if (offer.discountPercent().signum() > 0) {
                    String off = Fmt.percent(offer.discountPercent()) + " OFF";
                    g2.setFont(Theme.bold(11));
                    int tw = g2.getFontMetrics().stringWidth(off) + 16;
                    g2.setColor(Theme.ACCENT);
                    g2.fill(new RoundRectangle2D.Double(w - tw - 10, 10, tw, 22, 11, 11));
                    g2.setColor(Theme.ACCENT_INK);
                    g2.drawString(off, w - tw - 2, 25);
                }
                g2.dispose();
            }
        };
        art.setPreferredSize(new Dimension(10, 134));
        add(art, BorderLayout.NORTH);

        JPanel info = Ui.transparent(null);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(BorderFactory.createEmptyBorder(14, 4, 0, 0));
        JLabel name = Ui.label(v.name(), Theme.bold(16), Theme.TEXT);
        JLabel reg = Ui.label(v.reg() + "   ·   " + v.category(), Theme.mono(11.5f), Theme.TEXT_3);
        JPanel specs = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        specs.add(Ui.iconLabel(v.electric() ? "bolt" : "fuel", v.fuel(), Theme.TEXT_2));
        specs.add(Box.createHorizontalStrut(14));
        specs.add(Ui.iconLabel("gear", v.transmission(), Theme.TEXT_2));
        specs.add(Box.createHorizontalStrut(14));
        specs.add(Ui.iconLabel("seat", v.seats() + " seats", Theme.TEXT_2));
        for (JComponent c : new JComponent[]{name, reg, specs}) c.setAlignmentX(0f);
        info.add(name);
        info.add(Box.createVerticalStrut(4));
        info.add(reg);
        info.add(Box.createVerticalStrut(12));
        info.add(specs);
        add(info, BorderLayout.CENTER);

        JPanel bottom = Ui.transparent(new BorderLayout());
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), BorderFactory.createEmptyBorder(12, 4, 0, 0)));
        JLabel price = new JLabel("<html><span style='font-size:15pt'><b>" + Fmt.money(offer.dailyRate())
                + "</b></span><span style='color:" + Theme.hex(Theme.TEXT_3) + "'> /day</span></html>");
        price.setForeground(Theme.TEXT);
        price.setFont(Theme.font(13));
        JLabel total = Ui.label(Fmt.money(offer.total()) + " total for " + Fmt.days(offer.days()), Theme.font(12), Theme.TEXT_3);
        bottom.add(Ui.col(2, price, total), BorderLayout.CENTER);
        Btn book = new Btn("Book", Btn.Kind.PRIMARY);
        book.addActionListener(e -> onBook.run());
        JPanel bw = Ui.transparent(new GridBagLayout());
        bw.add(book);
        bottom.add(bw, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        hover(Theme.typeColor(v.bodyType()));
    }
}
