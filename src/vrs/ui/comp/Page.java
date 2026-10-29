package vrs.ui.comp;

import java.awt.*;
import javax.swing.*;

/** Vertical page that stretches to the scroll pane's width (so content wraps instead of scrolling sideways). */
public class Page extends JPanel implements Scrollable {
    public Page() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /** Adds a full-width child. */
    public Page add(JComponent c, int gapAfter) {
        c.setAlignmentX(0f);
        super.add(c);
        if (gapAfter > 0) super.add(Box.createVerticalStrut(gapAfter));
        return this;
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 60); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
