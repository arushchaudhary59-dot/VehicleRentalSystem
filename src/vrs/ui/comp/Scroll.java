package vrs.ui.comp;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import vrs.ui.Theme;

/** Scroll panes with thin, rounded, dark scroll bars. */
public final class Scroll {
    private Scroll() {}

    public static JScrollPane of(Component c) {
        JScrollPane sp = new JScrollPane(c);
        style(sp);
        return sp;
    }

    public static void style(JScrollPane sp) {
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        for (JScrollBar bar : new JScrollBar[]{sp.getVerticalScrollBar(), sp.getHorizontalScrollBar()}) {
            if (bar == null) continue;
            bar.setUI(new Thin());
            bar.setPreferredSize(new Dimension(10, 10));
            bar.setOpaque(false);
            bar.setUnitIncrement(18);
        }
        sp.setCorner(JScrollPane.UPPER_RIGHT_CORNER, filler());
    }

    private static JComponent filler() {
        JPanel p = new JPanel();
        p.setBackground(Theme.SURFACE);
        return p;
    }

    static final class Thin extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { trackColor = new Color(0, 0, 0, 0); }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = Theme.g2(g);
            boolean v = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            g2.setColor(isThumbRollover() || isDragging ? Theme.BORDER_HI : Theme.alpha(Theme.BORDER_HI, 150));
            if (v) g2.fill(new RoundRectangle2D.Double(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6));
            else g2.fill(new RoundRectangle2D.Double(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6));
            g2.dispose();
        }
    }
}
