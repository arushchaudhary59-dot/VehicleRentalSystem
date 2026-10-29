package vrs.ui.comp;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import vrs.ui.Theme;

/** Rounded panel. With hover(true) it lights up its border when the mouse is over it. */
public class Card extends JPanel {
    private int radius = 16;
    private Color fill = Theme.SURFACE;
    private Color border = Theme.BORDER;
    private Color hoverBorder = null;
    private float hover;
    private Timer anim;

    public Card() { this(new BorderLayout()); }

    public Card(LayoutManager lm) {
        super(lm);
        setOpaque(false);
    }

    public Card radius(int r) { radius = r; return this; }
    public Card fill(Color c) { fill = c; repaint(); return this; }
    public Card border(Color c) { border = c; repaint(); return this; }
    public Card pad(int t, int l, int b, int r) { setBorder(BorderFactory.createEmptyBorder(t, l, b, r)); return this; }
    public Card pad(int p) { return pad(p, p, p, p); }

    public Card hover(Color accent) {
        hoverBorder = accent;
        anim = new Timer(15, null);
        final boolean[] in = {false};
        anim.addActionListener(e -> {
            float target = in[0] ? 1 : 0;
            hover += (target - hover) * 0.22f;
            if (Math.abs(target - hover) < 0.02f) { hover = target; anim.stop(); }
            repaint();
        });
        MouseAdapter ma = new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { in[0] = true; anim.start(); }
            public void mouseExited(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), Card.this);
                if (!new Rectangle(0, 0, getWidth(), getHeight()).contains(p)) { in[0] = false; anim.start(); }
            }
        };
        addMouseListener(ma);
        // children should not "steal" the hover
        addContainerListener(new ContainerAdapter() {
            public void componentAdded(ContainerEvent e) { deepListen(e.getChild(), ma); }
        });
        for (Component c : getComponents()) deepListen(c, ma);
        return this;
    }

    private static void deepListen(Component c, MouseListener ml) {
        c.addMouseListener(ml);
        if (c instanceof Container ct) for (Component k : ct.getComponents()) deepListen(k, ml);
    }

    public float hoverAmount() { return hover; }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.g2(g);
        int w = getWidth(), h = getHeight();
        if (hoverBorder != null && hover > 0) {
            g2.setColor(Theme.alpha(hoverBorder, (int) (28 * hover)));
            g2.fill(new RoundRectangle2D.Double(-2, -2, w + 4, h + 4, radius + 4, radius + 4));
        }
        g2.setColor(hoverBorder != null ? Theme.mix(fill, Theme.SURFACE_2, hover * 0.6) : fill);
        g2.fill(new RoundRectangle2D.Double(0, 0, w, h, radius, radius));
        Color b = hoverBorder != null ? Theme.mix(border, hoverBorder, hover) : border;
        if (b != null) {
            g2.setColor(b);
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, radius, radius));
        }
        g2.dispose();
    }
}
