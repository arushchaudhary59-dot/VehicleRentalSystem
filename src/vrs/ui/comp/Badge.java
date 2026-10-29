package vrs.ui.comp;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import vrs.ui.Theme;

/** Small coloured status pill: "● Confirmed". */
public class Badge extends JComponent {
    private String text;
    private Color color;

    public Badge(String text) { this(text, Theme.statusColor(text)); }

    public Badge(String text, Color color) {
        this.text = text;
        this.color = color;
        setFont(Theme.bold(11.5f));
    }

    public void set(String text) { this.text = text; this.color = Theme.statusColor(text); revalidate(); repaint(); }

    @Override public Dimension getPreferredSize() {
        return new Dimension(getFontMetrics(getFont()).stringWidth(text) + 30, 24);
    }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override protected void paintComponent(Graphics g) {
        paint(Theme.g2(g), text, color, 0, (getHeight() - 24) / 2, getFont());
    }

    /** Also used by table renderers. Returns the width. */
    public static int paint(Graphics2D g2, String text, Color color, int x, int y, Font font) {
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text) + 30, h = 24;
        g2.setColor(Theme.alpha(color, 34));
        g2.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        g2.setColor(Theme.alpha(color, 90));
        g2.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, h, h));
        g2.setColor(color);
        g2.fill(new Ellipse2D.Double(x + 10, y + h / 2.0 - 3.5, 7, 7));
        g2.setColor(Theme.readable(color));
        g2.drawString(text, x + 22, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        return w;
    }
}
