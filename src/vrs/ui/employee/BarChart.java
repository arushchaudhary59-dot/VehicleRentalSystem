package vrs.ui.employee;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import vrs.service.StatsService.TypeStat;
import vrs.ui.*;

/**
 * Bars per vehicle type (one hue; the leading type is highlighted).
 * Each bar has the vehicle symbol under it. Hover shows exact values. Bars grow in when data changes.
 */
final class BarChart extends JComponent {
    private List<TypeStat> data = new ArrayList<>();
    private boolean revenue = true;
    private double progress = 1;
    private int hover = -1;
    private final Timer grow = new Timer(16, e -> {
        progress = Math.min(1, progress + 0.06);
        if (progress >= 1) ((Timer) e.getSource()).stop();
        repaint();
    });

    BarChart() {
        setPreferredSize(new Dimension(600, 330));
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                int h = indexAt(e.getX());
                if (h != hover) { hover = h; repaint(); }
            }
        });
        addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
        });
    }

    void setData(List<TypeStat> d) { data = d; progress = 0; grow.restart(); }
    void setRevenue(boolean r) { revenue = r; progress = 0; grow.restart(); }

    private double value(TypeStat s) { return revenue ? s.revenue().doubleValue() : s.rentals(); }

    /** Index of the type with the highest value, or -1 when everything is 0. */
    int leader() {
        int best = -1;
        for (int i = 0; i < data.size(); i++) if (value(data.get(i)) > 0 && (best < 0 || value(data.get(i)) > value(data.get(best)))) best = i;
        return best;
    }

    // plot geometry
    private static final int LEFT = 64, RIGHT = 16, TOP = 22, BOTTOM = 70;

    private int indexAt(int x) {
        if (data.isEmpty()) return -1;
        double slot = (getWidth() - LEFT - RIGHT) / (double) data.size();
        int i = (int) ((x - LEFT) / slot);
        return x < LEFT || i >= data.size() ? -1 : i;
    }

    private static double niceMax(double v) {
        if (v <= 0) return 4;
        double mag = Math.pow(10, Math.floor(Math.log10(v)));
        for (double m : new double[]{1, 2, 2.5, 5, 10}) if (m * mag >= v) return m * mag;
        return 10 * mag;
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.g2(g);
        int w = getWidth(), h = getHeight();
        int plotH = h - TOP - BOTTOM, plotW = w - LEFT - RIGHT;
        double max = 0;
        for (TypeStat s : data) max = Math.max(max, value(s));
        double top = niceMax(max);

        g2.setFont(Theme.font(11));
        for (int i = 0; i <= 4; i++) {                                     // recessive grid
            int y = TOP + plotH - (int) (plotH * i / 4.0);
            g2.setColor(i == 0 ? Theme.BORDER_HI : Theme.alpha(Theme.BORDER, 150));
            g2.drawLine(LEFT, y, w - RIGHT, y);
            double v = top * i / 4.0;
            String lab = revenue ? Fmt.moneyShort(BigDecimal.valueOf(v)) : (v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v));
            g2.setColor(Theme.TEXT_3);
            g2.drawString(lab, LEFT - 10 - g2.getFontMetrics().stringWidth(lab), y + 4);
        }
        if (data.isEmpty()) { g2.dispose(); return; }

        double slot = plotW / (double) data.size();
        double bw = Math.min(46, slot * 0.46);
        int lead = leader();
        double ease = 1 - Math.pow(1 - progress, 3);
        for (int i = 0; i < data.size(); i++) {
            TypeStat s = data.get(i);
            double cx = LEFT + slot * i + slot / 2;
            double bh = plotH * (value(s) / top) * ease;
            double x = cx - bw / 2, y = TOP + plotH - bh;
            if (i == hover) {
                g2.setColor(Theme.alpha(Theme.TEXT, 10));
                g2.fill(new RoundRectangle2D.Double(cx - slot / 2 + 4, TOP - 6, slot - 8, plotH + BOTTOM - 4, 10, 10));
            }
            Color c = i == lead ? Theme.ACCENT : i == hover ? Theme.mix(Theme.ACCENT, Theme.SURFACE, 0.25) : Theme.mix(Theme.ACCENT, Theme.SURFACE, 0.55);
            if (bh > 0.5) {
                Path2D bar = new Path2D.Double();                          // 4px rounded top, flat base
                double r = Math.min(4, bh);
                bar.moveTo(x, y + bh); bar.lineTo(x, y + r); bar.quadTo(x, y, x + r, y);
                bar.lineTo(x + bw - r, y); bar.quadTo(x + bw, y, x + bw, y + r); bar.lineTo(x + bw, y + bh); bar.closePath();
                g2.setColor(c);
                g2.fill(bar);
            }
            if (i == lead && progress >= 1) {                              // direct label on the leader only
                String v = revenue ? Fmt.moneyShort(s.revenue()) : String.valueOf(s.rentals());
                g2.setFont(Theme.bold(12));
                g2.setColor(Theme.TEXT);
                g2.drawString(v, (float) (cx - g2.getFontMetrics().stringWidth(v) / 2.0), (float) (y - 8));
            }
            Color tc = Theme.typeColor(s.bodyType());
            VehicleIcons.of(s.bodyType(), 48, i == lead || i == hover ? tc : Theme.mix(tc, Theme.TEXT_3, 0.55), false)
                    .paintIcon(this, g2, (int) (cx - 24), TOP + plotH + 10);
            g2.setFont(i == lead ? Theme.bold(12) : Theme.font(12));
            g2.setColor(i == lead || i == hover ? Theme.TEXT : Theme.TEXT_2);
            g2.drawString(s.bodyType(), (float) (cx - g2.getFontMetrics().stringWidth(s.bodyType()) / 2.0), TOP + plotH + 54);
        }

        if (hover >= 0 && hover < data.size()) {                         // tooltip
            TypeStat s = data.get(hover);
            String l1 = s.bodyType();
            String l2 = Fmt.money(s.revenue()) + " revenue";
            String l3 = s.rentals() + (s.rentals() == 1 ? " rental" : " rentals");
            g2.setFont(Theme.font(12));
            FontMetrics fm = g2.getFontMetrics();
            int tw = Math.max(fm.stringWidth(l2), Math.max(fm.stringWidth(l3), fm.stringWidth(l1))) + 24, th = 66;
            double cx = LEFT + slot * hover + slot / 2;
            double tx = Math.max(4, Math.min(w - tw - 4, cx + bw / 2 + 8));
            if (tx < cx && cx - bw / 2 - tw - 8 > 0) tx = cx - bw / 2 - tw - 8;
            double ty = TOP + 4;
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fill(new RoundRectangle2D.Double(tx + 2, ty + 3, tw, th, 10, 10));
            g2.setColor(Theme.SURFACE_3);
            g2.fill(new RoundRectangle2D.Double(tx, ty, tw, th, 10, 10));
            g2.setColor(Theme.BORDER_HI);
            g2.draw(new RoundRectangle2D.Double(tx + 0.5, ty + 0.5, tw - 1, th - 1, 10, 10));
            g2.setFont(Theme.bold(12.5f));
            g2.setColor(Theme.TEXT);
            g2.drawString(l1, (float) tx + 12, (float) ty + 20);
            g2.setFont(Theme.font(12));
            g2.setColor(Theme.TEXT_2);
            g2.drawString(l2, (float) tx + 12, (float) ty + 38);
            g2.drawString(l3, (float) tx + 12, (float) ty + 55);
        }
        g2.dispose();
    }
}
