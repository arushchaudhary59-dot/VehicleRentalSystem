package vrs.ui.comp;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import vrs.ui.*;

/** Date "field" that opens a calendar popup. Supports a minimum date and range highlighting. */
public class DateField extends JComponent {
    private LocalDate value;
    private LocalDate min;
    private LocalDate rangeFrom;   // highlights [rangeFrom, value] in the calendar
    private final String label;
    private final List<Consumer<LocalDate>> listeners = new ArrayList<>();
    private boolean over;

    public DateField(String label, LocalDate value) {
        this.label = label;
        this.value = value;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { if (isEnabled()) openPopup(); }
            public void mouseEntered(MouseEvent e) { over = true; repaint(); }
            public void mouseExited(MouseEvent e) { over = false; repaint(); }
        });
    }

    public LocalDate value() { return value; }
    public void setMin(LocalDate m) { min = m; }
    public void setRangeFrom(LocalDate d) { rangeFrom = d; }
    public void onChange(Consumer<LocalDate> l) { listeners.add(l); }

    public void setValue(LocalDate d, boolean notify) {
        value = d;
        repaint();
        if (notify) for (Consumer<LocalDate> l : listeners) l.accept(d);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(200, 56); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.g2(g);
        int w = getWidth(), h = getHeight();
        g2.setColor(over ? Theme.SURFACE_3 : Theme.SURFACE_2);
        g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 12, 12));
        g2.setColor(over ? Theme.BORDER_HI : Theme.BORDER);
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 12, 12));
        Icons.of("calendar", 20, Theme.ACCENT_HI).paintIcon(this, g2, 14, (h - 20) / 2);
        g2.setFont(Theme.font(11.5f));
        g2.setColor(Theme.TEXT_3);
        g2.drawString(label.toUpperCase(), 46, h / 2 - 5);
        g2.setFont(Theme.bold(14.5f));
        g2.setColor(Theme.TEXT);
        g2.drawString(value == null ? "Select date" : Fmt.day(value) + " " + value.getYear(), 46, h / 2 + 14);
        g2.dispose();
    }

    private void openPopup() {
        JPopupMenu pop = new JPopupMenu();
        pop.setBorder(BorderFactory.createEmptyBorder());
        pop.setOpaque(false);
        Calendar cal = new Calendar(value == null ? LocalDate.now() : value, d -> { pop.setVisible(false); setValue(d, true); });
        pop.add(cal);
        pop.show(this, 0, getHeight() + 6);
    }

    /** The month grid inside the popup. */
    private class Calendar extends JComponent {
        private YearMonth month;
        private final Consumer<LocalDate> pick;
        private LocalDate hoverDay;
        private static final int CELL = 38, PAD = 14, HEAD = 64;

        Calendar(LocalDate start, Consumer<LocalDate> pick) {
            this.month = YearMonth.from(start);
            this.pick = pick;
            setPreferredSize(new Dimension(CELL * 7 + PAD * 2, HEAD + CELL * 6 + PAD));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (e.getY() < 44) {
                        if (e.getX() < 56) month = month.minusMonths(1);
                        else if (e.getX() > getWidth() - 56) month = month.plusMonths(1);
                        repaint();
                        return;
                    }
                    LocalDate d = dayAt(e.getPoint());
                    if (d != null && allowed(d)) pick.accept(d);
                }
                public void mouseExited(MouseEvent e) { hoverDay = null; repaint(); }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) { hoverDay = dayAt(e.getPoint()); repaint(); }
            });
        }

        private boolean allowed(LocalDate d) { return min == null || !d.isBefore(min); }

        private LocalDate first() {
            LocalDate f = month.atDay(1);
            return f.minusDays(f.getDayOfWeek().getValue() - 1);   // weeks start on Monday
        }

        private LocalDate dayAt(Point p) {
            int col = (p.x - PAD) / CELL, row = (p.y - HEAD) / CELL;
            if (p.x < PAD || p.y < HEAD || col > 6 || row > 5) return null;
            return first().plusDays(row * 7L + col);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(Theme.SURFACE_2);
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 16, 16));
            g2.setColor(Theme.BORDER_HI);
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 16, 16));
            g2.setFont(Theme.bold(14));
            g2.setColor(Theme.TEXT);
            String title = Fmt.month(month);
            g2.drawString(title, (w - g2.getFontMetrics().stringWidth(title)) / 2, 28);
            Icons.of("chevLeft", 18, Theme.TEXT_2).paintIcon(this, g2, PAD + 4, 14);
            Icons.of("chevRight", 18, Theme.TEXT_2).paintIcon(this, g2, w - PAD - 22, 14);
            g2.setFont(Theme.bold(11));
            g2.setColor(Theme.TEXT_3);
            for (int i = 0; i < 7; i++) {
                String dn = DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).substring(0, 2);
                g2.drawString(dn, PAD + i * CELL + (CELL - g2.getFontMetrics().stringWidth(dn)) / 2, 54);
            }
            LocalDate d = first();
            LocalDate today = LocalDate.now();
            for (int r = 0; r < 6; r++) for (int c = 0; c < 7; c++, d = d.plusDays(1)) {
                int x = PAD + c * CELL, y = HEAD + r * CELL;
                boolean inMonth = YearMonth.from(d).equals(month);
                boolean ok = allowed(d);
                boolean sel = d.equals(value);
                boolean inRange = rangeFrom != null && value != null && !d.isBefore(rangeFrom) && !d.isAfter(value);
                if (inRange && !sel) {
                    g2.setColor(Theme.alpha(Theme.ACCENT, 30));
                    g2.fill(new Rectangle2D.Double(x, y + 4, CELL, CELL - 8));
                }
                if (sel) {
                    g2.setColor(Theme.ACCENT);
                    g2.fill(new RoundRectangle2D.Double(x + 3, y + 3, CELL - 6, CELL - 6, 10, 10));
                } else if (d.equals(hoverDay) && ok) {
                    g2.setColor(Theme.SURFACE_3);
                    g2.fill(new RoundRectangle2D.Double(x + 3, y + 3, CELL - 6, CELL - 6, 10, 10));
                }
                if (d.equals(today) && !sel) {
                    g2.setColor(Theme.ACCENT);
                    g2.fill(new Ellipse2D.Double(x + CELL / 2.0 - 2, y + CELL - 9, 4, 4));
                }
                g2.setFont(sel ? Theme.bold(13) : Theme.font(13));
                g2.setColor(sel ? Theme.ACCENT_INK : !ok ? Theme.alpha(Theme.TEXT_3, 90) : inMonth ? Theme.TEXT : Theme.TEXT_3);
                String s = String.valueOf(d.getDayOfMonth());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(s, x + (CELL - fm.stringWidth(s)) / 2, y + (CELL - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }
}
