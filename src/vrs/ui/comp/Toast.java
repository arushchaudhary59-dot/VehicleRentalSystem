package vrs.ui.comp;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import vrs.ui.*;

/** Small notification that slides in at the bottom-right of the window and fades away. */
public final class Toast extends JComponent {
    public enum Kind { SUCCESS, ERROR, INFO }

    private final String message;
    private final Kind kind;
    private float alpha = 0f;
    private int age;

    private Toast(String message, Kind kind) {
        this.message = message;
        this.kind = kind;
        setFont(Theme.font(13.5f));
    }

    public static void success(String m) { show(m, Kind.SUCCESS); }
    public static void error(String m) { show(m, Kind.ERROR); }
    public static void info(String m) { show(m, Kind.INFO); }

    public static void show(String message, Kind kind) {
        Window w = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        while (w instanceof Dialog && w.getOwner() != null) w = w.getOwner();     // show on the main window
        JRootPane root = w instanceof JFrame jf ? jf.getRootPane() : null;
        if (root == null) {
            for (Frame f : Frame.getFrames()) if (f.isVisible() && f instanceof JFrame jf) { root = jf.getRootPane(); break; }
        }
        if (root == null) { System.out.println("[" + kind + "] " + message); return; }
        JLayeredPane lp = root.getLayeredPane();
        Toast t = new Toast(message, kind);
        Dimension d = t.getPreferredSize();
        int stack = 0;
        for (Component c : lp.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) if (c instanceof Toast) stack += c.getHeight() + 10;
        int x = lp.getWidth() - d.width - 24, y0 = lp.getHeight() - d.height - 24 - stack;
        t.setBounds(x, y0 + 20, d.width, d.height);
        lp.add(t, JLayeredPane.POPUP_LAYER);
        int life = 3200 + Math.min(4000, message.length() * 25);
        Timer timer = new Timer(16, null);
        timer.addActionListener(e -> {
            t.age += 16;
            if (t.age < 220) { t.alpha = t.age / 220f; t.setLocation(x, y0 + (int) (20 * (1 - t.alpha))); }
            else if (t.age > life) { t.alpha = Math.max(0, 1 - (t.age - life) / 300f); }
            t.repaint();
            if (t.age > life + 300) { timer.stop(); lp.remove(t); lp.repaint(); }
        });
        timer.start();
    }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int w = Math.min(460, fm.stringWidth(message) + 70);
        int lines = Math.max(1, (int) Math.ceil(fm.stringWidth(message) / (double) (w - 70)));
        return new Dimension(w, 26 + lines * fm.getHeight());
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.g2(g);
        g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
        int w = getWidth(), h = getHeight();
        Color c = switch (kind) { case SUCCESS -> Theme.ACCENT; case ERROR -> Theme.DANGER; default -> Theme.INFO; };
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fill(new RoundRectangle2D.Double(2, 4, w - 2, h - 2, 14, 14));
        g2.setColor(Theme.SURFACE_3);
        g2.fill(new RoundRectangle2D.Double(0, 0, w - 2, h - 4, 14, 14));
        g2.setColor(Theme.alpha(c, 160));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 3, h - 5, 14, 14));
        g2.setColor(c);
        g2.fill(new RoundRectangle2D.Double(0, 10, 3.5, h - 24, 3, 3));
        Icons.of(kind == Kind.SUCCESS ? "check" : kind == Kind.ERROR ? "alert" : "info", 18, c).paintIcon(this, g2, 16, 13);
        g2.setFont(getFont());
        g2.setColor(Theme.TEXT);
        FontMetrics fm = g2.getFontMetrics();
        int y = 13 + fm.getAscent(), x = 46, maxW = w - 64;
        StringBuilder line = new StringBuilder();
        for (String word : message.split(" ")) {
            if (fm.stringWidth(line + word) > maxW && line.length() > 0) {
                g2.drawString(line.toString(), x, y);
                y += fm.getHeight();
                line.setLength(0);
            }
            line.append(word).append(' ');
        }
        g2.drawString(line.toString(), x, y);
        g2.dispose();
    }
}
