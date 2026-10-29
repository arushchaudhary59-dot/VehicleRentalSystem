package vrs.ui.comp;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import vrs.ui.Theme;

/** Segmented control with a sliding highlight (e.g. Customer | Employee). */
public class Seg extends JComponent {
    private final List<String> options;
    private int selected;
    private double slideX = -1;
    private final Timer anim;
    private final List<Consumer<String>> listeners = new ArrayList<>();
    private int hover = -1;

    public Seg(String... options) {
        this.options = List.of(options);
        setFont(Theme.bold(13));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        anim = new Timer(15, e -> {
            double target = segX(selected);
            slideX += (target - slideX) * 0.28;
            if (Math.abs(target - slideX) < 0.5) { slideX = target; ((Timer) e.getSource()).stop(); }
            repaint();
        });
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { select(indexAt(e.getX()), true); }
            public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) { hover = indexAt(e.getX()); repaint(); }
        });
    }

    public void onChange(Consumer<String> l) { listeners.add(l); }
    public String value() { return options.get(selected); }
    public int index() { return selected; }

    public void select(int i, boolean notify) {
        if (i < 0 || i >= options.size()) return;
        boolean changed = i != selected;
        selected = i;
        anim.start();
        if (changed && notify) for (Consumer<String> l : listeners) l.accept(options.get(i));
    }

    public void select(String value) { select(options.indexOf(value), false); }

    private int indexAt(int x) {
        int i = (int) ((x - 4) / segW());
        return Math.max(0, Math.min(options.size() - 1, i));
    }

    private double segW() { return (getWidth() - 8) / (double) options.size(); }
    private double segX(int i) { return 4 + i * segW(); }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int max = 0;
        for (String o : options) max = Math.max(max, fm.stringWidth(o));
        return new Dimension((max + 32) * options.size() + 8, 40);
    }
    @Override public Dimension getMaximumSize() { return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize(); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.g2(g);
        int w = getWidth(), h = getHeight();
        if (slideX < 0) slideX = segX(selected);
        g2.setColor(Theme.SURFACE_2);
        g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 12, 12));
        g2.setColor(Theme.BORDER);
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 12, 12));
        g2.setColor(Theme.SURFACE_3);
        g2.fill(new RoundRectangle2D.Double(slideX, 4, segW(), h - 8, 9, 9));
        g2.setColor(Theme.alpha(Theme.ACCENT, 160));
        g2.fill(new RoundRectangle2D.Double(slideX + segW() / 2 - 10, h - 6, 20, 2.5, 2, 2));
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < options.size(); i++) {
            String o = options.get(i);
            double x = segX(i) + (segW() - fm.stringWidth(o)) / 2;
            g2.setColor(i == selected ? Theme.TEXT : i == hover ? Theme.TEXT_2 : Theme.TEXT_3);
            g2.drawString(o, (float) x, (h - fm.getHeight()) / 2f + fm.getAscent() - 1);
        }
        g2.dispose();
    }
}
