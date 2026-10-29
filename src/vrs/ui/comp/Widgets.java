package vrs.ui.comp;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import vrs.ui.*;

/** Smaller visual pieces: avatar, loading spinner, empty state, stat card, responsive grid. */
public final class Widgets {
    private Widgets() {}

    /** Round avatar with initials. */
    public static JComponent avatar(String initials, int size, Color color) {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                g2.setPaint(new GradientPaint(0, 0, Theme.mix(color, Color.WHITE, 0.2), size, size, Theme.mix(color, Color.BLACK, 0.3)));
                g2.fill(new Ellipse2D.Double(0, 0, size, size));
                g2.setFont(Theme.bold(size * 0.38f));
                g2.setColor(Theme.ACCENT_INK);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(initials, (size - fm.stringWidth(initials)) / 2f, (size - fm.getHeight()) / 2f + fm.getAscent());
                g2.dispose();
            }
        };
        Dimension d = new Dimension(size, size);
        c.setPreferredSize(d); c.setMinimumSize(d); c.setMaximumSize(d);
        return c;
    }

    /** Spinning arc + text, shown while data loads. */
    public static JComponent loading(String text) {
        JComponent c = new JComponent() {
            double angle;
            final Timer t = new Timer(16, e -> { angle += 7; repaint(); });
            @Override public void addNotify() { super.addNotify(); t.start(); }
            @Override public void removeNotify() { t.stop(); super.removeNotify(); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                int cx = getWidth() / 2, cy = getHeight() / 2 - 12;
                g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(Theme.BORDER);
                g2.draw(new Ellipse2D.Double(cx - 16, cy - 16, 32, 32));
                g2.setColor(Theme.ACCENT);
                g2.draw(new Arc2D.Double(cx - 16, cy - 16, 32, 32, -angle, 100, Arc2D.OPEN));
                g2.setFont(Theme.font(13));
                g2.setColor(Theme.TEXT_3);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + 40);
                g2.dispose();
            }
        };
        c.setPreferredSize(new Dimension(300, 160));
        return c;
    }

    /** Big icon + title + hint, used when a list is empty. */
    public static JComponent empty(String icon, String title, String hint) {
        JPanel p = Ui.transparent(new GridBagLayout());
        JPanel col = Ui.transparent(null);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JLabel ic = new JLabel(Icons.of(icon, 40, Theme.TEXT_3));
        ic.setAlignmentX(0.5f);
        JLabel t = Ui.label(title, Theme.bold(16), Theme.TEXT);
        t.setAlignmentX(0.5f);
        JLabel h = Ui.label(hint, Theme.font(13), Theme.TEXT_3);
        h.setAlignmentX(0.5f);
        col.add(ic); col.add(Box.createVerticalStrut(12)); col.add(t); col.add(Box.createVerticalStrut(6)); col.add(h);
        p.add(col);
        p.setPreferredSize(new Dimension(300, 220));
        return p;
    }

    /** Dashboard metric card. */
    public static Card stat(String icon, Color color, String label, JLabel value, JLabel note) {
        Card c = new Card(new BorderLayout(14, 0)).pad(18, 18, 18, 18);
        JComponent ic = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                g2.setColor(Theme.alpha(color, 36));
                g2.fill(new RoundRectangle2D.Double(0, 0, 44, 44, 12, 12));
                Icons.of(icon, 22, color).paintIcon(this, g2, 11, 11);
                g2.dispose();
            }
        };
        ic.setPreferredSize(new Dimension(44, 44));
        JPanel left = Ui.transparent(new BorderLayout());
        left.add(ic, BorderLayout.NORTH);
        c.add(left, BorderLayout.WEST);
        value.setFont(Theme.bold(24));
        value.setForeground(Theme.TEXT);
        note.setFont(Theme.font(12));
        note.setForeground(Theme.TEXT_3);
        c.add(Ui.col(2, Ui.label(label, Theme.font(12.5f), Theme.TEXT_2), value, note), BorderLayout.CENTER);
        c.hover(color);
        return c;
    }

    /**
     * Panel that lays children out in as many equal columns as fit
     * (each at least minWidth wide). Use inside a JScrollPane.
     */
    public static class Grid extends JPanel implements Scrollable {
        private final int minWidth, itemHeight, gap;

        public Grid(int minWidth, int itemHeight, int gap) {
            this.minWidth = minWidth; this.itemHeight = itemHeight; this.gap = gap;
            setOpaque(false);
            setLayout(new LayoutManager() {
                public void addLayoutComponent(String n, Component c) { }
                public void removeLayoutComponent(Component c) { }
                public Dimension preferredLayoutSize(Container p) {
                    int w = width(p);
                    int cols = cols(w), n = p.getComponentCount();
                    int rows = (n + cols - 1) / cols;
                    Insets in = p.getInsets();
                    return new Dimension(w, rows * itemHeight + Math.max(0, rows - 1) * gap + in.top + in.bottom);
                }
                public Dimension minimumLayoutSize(Container p) { return new Dimension(minWidth, itemHeight); }
                public void layoutContainer(Container p) {
                    Insets in = p.getInsets();
                    int w = p.getWidth() - in.left - in.right;
                    int cols = cols(p.getWidth());
                    int cw = (w - (cols - 1) * gap) / cols;
                    for (int i = 0; i < p.getComponentCount(); i++) {
                        int r = i / cols, c = i % cols;
                        p.getComponent(i).setBounds(in.left + c * (cw + gap), in.top + r * (itemHeight + gap), cw, itemHeight);
                    }
                }
            });
        }

        private int width(Container p) {
            Container parent = p.getParent();
            int w = parent instanceof JViewport vp ? vp.getWidth() : p.getWidth();
            return Math.max(w, minWidth);
        }

        private int cols(int width) {
            Insets in = getInsets();
            int w = width - in.left - in.right;
            return Math.max(1, (w + gap) / (minWidth + gap));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
