package vrs.ui;

import java.awt.*;
import javax.swing.*;

/** Tiny layout helpers so screen code stays short. */
public final class Ui {
    private Ui() {}

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** Wraps a component so BoxLayout aligns it to the left. */
    public static JComponent left(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return p;
    }

    /** Horizontal row of components with a gap. */
    public static JPanel row(int gap, Component... cs) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        for (int i = 0; i < cs.length; i++) {
            if (i > 0) p.add(Box.createHorizontalStrut(gap));
            p.add(cs[i]);
        }
        return p;
    }

    /** Vertical stack, children left-aligned. */
    public static JPanel col(int gap, JComponent... cs) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        for (int i = 0; i < cs.length; i++) {
            if (i > 0) p.add(Box.createVerticalStrut(gap));
            cs[i].setAlignmentX(0f);
            p.add(cs[i]);
        }
        return p;
    }

    public static JPanel transparent(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    public static JComponent iconLabel(String icon, String text, Color c) {
        JLabel l = label(text, Theme.font(12.5f), c);
        l.setIcon(Icons.of(icon, 14, Theme.TEXT_3));
        l.setIconTextGap(6);
        return l;
    }

    /** A hairline divider. */
    public static JComponent divider() {
        JComponent d = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(0, getHeight() / 2, getWidth(), 1);
            }
        };
        d.setPreferredSize(new Dimension(10, 9));
        d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 9));
        d.setAlignmentX(0f);
        return d;
    }
}
