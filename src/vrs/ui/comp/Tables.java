package vrs.ui.comp;

import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import vrs.ui.*;

/** Styling and cell renderers for the dark data tables. */
public final class Tables {
    private Tables() {}

    public static JTable create(TableModel model) {
        JTable t = new JTable(model) {
            @Override public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                Object hover = getClientProperty("hoverRow");
                boolean sel = isRowSelected(row);
                Color bg = sel ? Theme.SURFACE_3 : (hover instanceof Integer h && h == row) ? Theme.SURFACE_2 : Theme.SURFACE;
                c.setBackground(bg);
                return c;
            }
        };
        t.setRowHeight(46);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(Theme.alpha(Theme.BORDER, 160));
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setFillsViewportHeight(true);
        t.setBackground(Theme.SURFACE);
        t.setForeground(Theme.TEXT);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFont(Theme.font(13));
        t.setAutoCreateRowSorter(true);
        t.setDefaultRenderer(Object.class, new Text());
        t.setDefaultRenderer(Integer.class, new Text());
        t.setDefaultRenderer(Long.class, new Text());
        t.setDefaultRenderer(BigDecimal.class, new Money());
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(10, 38));
        h.setDefaultRenderer(new Header());
        h.setBackground(Theme.SURFACE);
        t.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                int r = t.rowAtPoint(e.getPoint());
                if (!Integer.valueOf(r).equals(t.getClientProperty("hoverRow"))) { t.putClientProperty("hoverRow", r); t.repaint(); }
            }
        });
        t.addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) { t.putClientProperty("hoverRow", -1); t.repaint(); }
        });
        return t;
    }

    /** Table inside a rounded card with a scroll pane. */
    public static Card inCard(JTable t) {
        JScrollPane sp = Scroll.of(t);
        sp.getViewport().setBackground(Theme.SURFACE);
        sp.setColumnHeader(new JViewport());
        sp.getColumnHeader().setOpaque(false);
        Card c = new Card(new BorderLayout());
        c.pad(6, 6, 6, 6);
        c.add(sp);
        return c;
    }

    public static void widths(JTable t, int... w) {
        for (int i = 0; i < w.length && i < t.getColumnCount(); i++) {
            TableColumn c = t.getColumnModel().getColumn(i);
            c.setPreferredWidth(w[i]);
            if (w[i] < 70) { c.setMaxWidth(w[i] + 20); }
        }
    }

    // ------------------------------------------------------------------ renderers
    public static class Text extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            super.getTableCellRendererComponent(t, v, s, false, r, c);
            setBorder(new EmptyBorder(0, 14, 0, 10));
            setForeground(Theme.TEXT);
            setFont(Theme.font(13));
            if (v instanceof Number) setHorizontalAlignment(RIGHT); else setHorizontalAlignment(LEFT);
            return this;
        }
    }

    public static class Muted extends Text {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            super.getTableCellRendererComponent(t, v, s, f, r, c);
            setForeground(Theme.TEXT_2);
            setFont(Theme.font(12.5f));
            return this;
        }
    }

    public static class Mono extends Text {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            super.getTableCellRendererComponent(t, v, s, f, r, c);
            setFont(Theme.mono(12));
            setForeground(Theme.TEXT_2);
            return this;
        }
    }

    public static class Money extends Text {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            super.getTableCellRendererComponent(t, v instanceof BigDecimal b ? Fmt.money(b) : v, s, f, r, c);
            setHorizontalAlignment(RIGHT);
            setBorder(new EmptyBorder(0, 10, 0, 16));
            setFont(Theme.bold(13));
            return this;
        }
    }

    /** Draws the cell value as a coloured status pill. */
    public static class BadgeCell extends JComponent implements TableCellRenderer {
        private String text;
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            text = v == null ? "" : v.toString();
            return this;
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (!text.isEmpty()) Badge.paint(g2, text, Theme.statusColor(text), 12, (getHeight() - 24) / 2, Theme.bold(11.5f));
            g2.dispose();
        }
    }

    /** Vehicle symbol + name. The value must be "BodyType|Name|Sub". */
    public static class VehicleCell extends JComponent implements TableCellRenderer {
        private String body = "", name = "", sub = "";
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            String[] p = (v == null ? "||" : v.toString()).split("\\|", -1);
            body = p[0]; name = p.length > 1 ? p[1] : ""; sub = p.length > 2 ? p[2] : "";
            return this;
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            VehicleIcons.of(body, 44, false).paintIcon(this, g2, 10, (getHeight() - 24) / 2);
            g2.setFont(Theme.bold(13));
            g2.setColor(Theme.TEXT);
            int y = sub.isEmpty() ? getHeight() / 2 + 5 : getHeight() / 2 - 2;
            g2.drawString(fit(g2, name, getWidth() - 70), 62, y);
            if (!sub.isEmpty()) {
                g2.setFont(Theme.font(11.5f));
                g2.setColor(Theme.TEXT_3);
                g2.drawString(fit(g2, sub, getWidth() - 70), 62, y + 16);
            }
            g2.dispose();
        }
    }

    /** Two-line text cell: "Main|Sub". */
    public static class TwoLine extends JComponent implements TableCellRenderer {
        private String main = "", sub = "";
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            String[] p = (v == null ? "|" : v.toString()).split("\\|", -1);
            main = p[0]; sub = p.length > 1 ? p[1] : "";
            return this;
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setFont(Theme.font(13));
            g2.setColor(Theme.TEXT);
            g2.drawString(fit(g2, main, getWidth() - 22), 14, getHeight() / 2 - 2);
            g2.setFont(Theme.font(11.5f));
            g2.setColor(Theme.TEXT_3);
            g2.drawString(fit(g2, sub, getWidth() - 22), 14, getHeight() / 2 + 14);
            g2.dispose();
        }
    }

    /** Shortens text with "…" so it fits in the given width. */
    static String fit(Graphics2D g2, String s, int max) {
        FontMetrics fm = g2.getFontMetrics();
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s.trim() + "…";
    }

    static class Header extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            super.getTableCellRendererComponent(t, v == null ? "" : v.toString().toUpperCase(), false, false, r, c);
            setFont(Theme.bold(11));
            setForeground(Theme.TEXT_3);
            setBackground(Theme.SURFACE);
            setOpaque(true);
            setHorizontalAlignment(t.getColumnClass(c) == BigDecimal.class || Number.class.isAssignableFrom(t.getColumnClass(c)) ? RIGHT : LEFT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    new EmptyBorder(0, 14, 0, t.getColumnClass(c) == BigDecimal.class ? 16 : 10)));
            return this;
        }
    }

    /** Simple read-only table model backed by rows of Object[]. */
    public static class RowModel extends AbstractTableModel {
        private final String[] cols;
        private final Class<?>[] types;
        private java.util.List<Object[]> rows = new java.util.ArrayList<>();

        public RowModel(String[] cols, Class<?>[] types) { this.cols = cols; this.types = types; }
        public void setRows(java.util.List<Object[]> r) { rows = r; fireTableDataChanged(); }
        public Object[] row(int i) { return rows.get(i); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) { return types[c]; }
        @Override public Object getValueAt(int r, int c) { return rows.get(r)[c]; }
    }
}
