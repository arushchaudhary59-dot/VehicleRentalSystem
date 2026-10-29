package vrs.ui.comp;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import vrs.ui.*;

/** Rounded button with hover animation. */
public class Btn extends JButton {
    public enum Kind { PRIMARY, SECONDARY, GHOST, DANGER, SUBTLE }

    private final Kind kind;
    private String iconName;
    private float hover;            // 0..1 animated
    private boolean over, pressed;
    private final Timer anim;
    private boolean small;

    public Btn(String text, Kind kind) { this(text, kind, null); }

    public Btn(String text, Kind kind, String iconName) {
        super(text);
        this.kind = kind;
        this.iconName = iconName;
        setFont(Theme.bold(13));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (kind == Kind.GHOST) setFocusable(false);
        anim = new Timer(15, e -> {
            float target = over && isEnabled() ? 1f : 0f;
            hover += (target - hover) * 0.25f;
            if (Math.abs(target - hover) < 0.02f) { hover = target; ((Timer) e.getSource()).stop(); }
            repaint();
        });
        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { over = true; anim.start(); }
            public void mouseExited(MouseEvent e) { over = false; pressed = false; anim.start(); }
            public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
            public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
        });
    }

    public Btn small() { small = true; setFont(Theme.bold(12)); revalidate(); return this; }
    public Btn icon(String name) { this.iconName = name; revalidate(); repaint(); return this; }
    public Btn tip(String t) { setToolTipText(t); return this; }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        String t = getText() == null ? "" : getText();
        int iconW = iconName == null ? 0 : (small ? 14 : 16) + (t.isEmpty() ? 0 : 8);
        int padX = t.isEmpty() ? (small ? 9 : 11) : (small ? 12 : 16);
        int h = small ? 32 : 40;
        return new Dimension(fm.stringWidth(t) + iconW + padX * 2, h);
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize(); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.g2(g);
        int w = getWidth(), h = getHeight();
        Color bg, fg, border = null;
        switch (kind) {
            case PRIMARY -> { bg = Theme.mix(Theme.ACCENT, Theme.ACCENT_HI, hover); fg = Theme.ACCENT_INK; }
            case DANGER -> { bg = Theme.mix(Theme.alpha(Theme.DANGER, 40), Theme.alpha(Theme.DANGER, 70), hover); fg = Theme.readable(Theme.DANGER); border = Theme.alpha(Theme.DANGER, 110); }
            case SECONDARY -> { bg = Theme.mix(Theme.SURFACE_2, Theme.SURFACE_3, hover); fg = Theme.TEXT; border = Theme.mix(Theme.BORDER, Theme.BORDER_HI, hover); }
            case SUBTLE -> { bg = Theme.mix(Theme.alpha(Theme.ACCENT, 28), Theme.alpha(Theme.ACCENT, 52), hover); fg = Theme.ACCENT_HI; }
            default -> { bg = Theme.alpha(Theme.SURFACE_3, (int) (hover * 255)); fg = Theme.mix(Theme.TEXT_2, Theme.TEXT, hover); }
        }
        if (!isEnabled()) { bg = Theme.alpha(Theme.SURFACE_2, 160); fg = Theme.TEXT_3; border = Theme.BORDER; }
        if (pressed && isEnabled()) bg = Theme.mix(bg, Color.BLACK, 0.15);
        RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 10, 10);
        g2.setColor(bg);
        g2.fill(r);
        if (border != null) { g2.setColor(border); g2.draw(r); }
        if (hasFocus() && isEnabled() && kind != Kind.PRIMARY) {
            g2.setColor(Theme.alpha(Theme.ACCENT, 120));
            g2.draw(new RoundRectangle2D.Double(1.5, 1.5, w - 3, h - 3, 9, 9));
        }
        FontMetrics fm = g2.getFontMetrics(getFont());
        String t = getText() == null ? "" : getText();
        int is = small ? 14 : 16;
        int iconW = iconName == null ? 0 : is + (t.isEmpty() ? 0 : 8);
        int x = (w - (fm.stringWidth(t) + iconW)) / 2;
        if (iconName != null) {
            Icons.of(iconName, is, fg).paintIcon(this, g2, x, (h - is) / 2);
            x += iconW;
        }
        g2.setFont(getFont());
        g2.setColor(fg);
        g2.drawString(t, x, (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
