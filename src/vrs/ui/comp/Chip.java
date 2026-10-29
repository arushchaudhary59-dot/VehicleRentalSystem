package vrs.ui.comp;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import vrs.ui.*;

/** Pill-shaped toggle used for filters. Can show a small icon or a vehicle symbol. */
public class Chip extends JToggleButton {
    private String iconName;
    private String vehicleType;
    private Color accent = Theme.ACCENT;
    private boolean over;

    public Chip(String text) {
        super(text);
        setFont(Theme.font(13));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { over = true; repaint(); }
            public void mouseExited(MouseEvent e) { over = false; repaint(); }
        });
    }

    public Chip icon(String name) { iconName = name; return this; }
    public Chip vehicle(String bodyType) { vehicleType = bodyType; return this; }
    public Chip accent(Color c) { accent = c; return this; }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Theme.bold(13));
        int extra = vehicleType != null ? 40 : iconName != null ? 22 : 0;
        return new Dimension(fm.stringWidth(getText()) + 28 + extra, vehicleType != null ? 40 : 34);
    }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.g2(g);
        int w = getWidth(), h = getHeight();
        boolean sel = isSelected();
        RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, h, h);
        g2.setColor(sel ? Theme.alpha(accent, 38) : over ? Theme.SURFACE_3 : Theme.SURFACE_2);
        g2.fill(r);
        g2.setColor(sel ? Theme.alpha(accent, 200) : over ? Theme.BORDER_HI : Theme.BORDER);
        g2.draw(r);
        int x = 14;
        if (vehicleType != null) {
            VehicleIcons.of(vehicleType, 32, sel ? Theme.typeColor(vehicleType) : Theme.mix(Theme.typeColor(vehicleType), Theme.TEXT_3, 0.45), false)
                    .paintIcon(this, g2, x - 2, (h - 17) / 2);
            x += 38;
        } else if (iconName != null) {
            Icons.of(iconName, 15, sel ? Theme.ACCENT_HI : Theme.TEXT_2).paintIcon(this, g2, x, (h - 15) / 2);
            x += 22;
        }
        g2.setFont(sel ? Theme.bold(13) : Theme.font(13));
        g2.setColor(sel ? Theme.TEXT : over ? Theme.TEXT : Theme.TEXT_2);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(getText(), x, (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
