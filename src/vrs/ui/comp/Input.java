package vrs.ui.comp;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import vrs.ui.*;

/** Text inputs with rounded dark background, placeholder, leading icon and focus ring. */
public final class Input {
    private Input() {}

    public static JTextField text(String placeholder, String icon) {
        JTextField f = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                paintBox(this, g, placeholder, icon);
                super.paintComponent(g);
                paintPlaceholder(this, g, placeholder, icon);
            }
        };
        setup(f, icon, 12);
        return f;
    }

    /** Password field with a show / hide eye button on the right. */
    public static JPasswordField password(String placeholder) {
        final boolean[] visible = {false};
        JPasswordField f = new JPasswordField() {
            @Override protected void paintComponent(Graphics g) {
                paintBox(this, g, placeholder, "lock");
                super.paintComponent(g);
                paintPlaceholder(this, g, placeholder, "lock");
                Icons.of(visible[0] ? "eyeOff" : "eye", 16, Theme.TEXT_3).paintIcon(this, g, getWidth() - 30, (getHeight() - 16) / 2);
            }
        };
        setup(f, "lock", 38);
        f.setEchoChar('•');
        f.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getX() > f.getWidth() - 36) {
                    visible[0] = !visible[0];
                    f.setEchoChar(visible[0] ? (char) 0 : '•');
                    f.repaint();
                }
            }
        });
        f.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                f.setCursor(Cursor.getPredefinedCursor(e.getX() > f.getWidth() - 36 ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
            }
        });
        return f;
    }

    public static JTextArea area(int rows) {
        JTextArea a = new JTextArea(rows, 20);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(new EmptyBorder(10, 12, 10, 12));
        a.setFont(Theme.font(13));
        return a;
    }

    private static void setup(JTextComponent f, String icon, int rightPad) {
        f.setOpaque(false);
        f.setFont(Theme.font(14));
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Theme.ACCENT_HI);
        f.setSelectionColor(Theme.alpha(Theme.ACCENT, 80));
        f.setSelectedTextColor(Theme.TEXT);
        f.setBorder(new EmptyBorder(0, icon == null ? 14 : 40, 0, rightPad));
        f.setPreferredSize(new Dimension(220, 44));
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { f.repaint(); }
            public void focusLost(FocusEvent e) { f.repaint(); }
        });
    }

    static void paintBox(JComponent c, Graphics g, String placeholder, String icon) {
        Graphics2D g2 = Theme.g2(g);
        int w = c.getWidth(), h = c.getHeight();
        boolean focus = c.hasFocus();
        boolean error = Boolean.TRUE.equals(c.getClientProperty("error"));
        g2.setColor(c.isEnabled() ? Theme.SURFACE_2 : Theme.SURFACE);
        g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 12, 12));
        Color border = error ? Theme.DANGER : focus ? Theme.ACCENT : Theme.BORDER;
        g2.setColor(border);
        g2.setStroke(new BasicStroke(focus || error ? 1.5f : 1f));
        g2.draw(new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 12, 12));
        if (focus) {
            g2.setColor(Theme.alpha(Theme.ACCENT, 35));
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new RoundRectangle2D.Double(-1, -1, w + 2, h + 2, 14, 14));
        }
        if (icon != null) Icons.of(icon, 17, focus ? Theme.ACCENT_HI : Theme.TEXT_3).paintIcon(c, g2, 14, (h - 17) / 2);
        g2.dispose();
    }

    static void paintPlaceholder(JTextComponent c, Graphics g, String placeholder, String icon) {
        if (placeholder == null || c.getDocument().getLength() > 0) return;
        Graphics2D g2 = Theme.g2(g);
        g2.setFont(c.getFont());
        g2.setColor(Theme.TEXT_3);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(placeholder, c.getInsets().left, (c.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    /** Marks a field red until the user types in it again. */
    public static void error(JComponent f, boolean on) {
        f.putClientProperty("error", on);
        f.repaint();
        if (on && f instanceof JTextComponent tc) {
            tc.addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) { error(f, false); tc.removeKeyListener(this); }
            });
        }
    }
}
