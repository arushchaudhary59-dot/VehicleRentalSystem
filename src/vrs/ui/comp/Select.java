package vrs.ui.comp;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.*;
import vrs.ui.*;

/** Dark rounded drop-down. */
public class Select<T> extends JComboBox<T> {

    public Select(List<T> items) {
        super(new DefaultComboBoxModel<>(new java.util.Vector<>(items)));
        setFont(Theme.font(13.5f));
        setForeground(Theme.TEXT);
        setBackground(Theme.SURFACE_2);
        setOpaque(false);
        setBorder(new EmptyBorder(0, 0, 0, 0));
        setMaximumRowCount(12);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override protected void paintComponent(Graphics g) {
                        Icons.of("chevDown", 16, Theme.TEXT_2).paintIcon(this, g, (getWidth() - 16) / 2 - 4, (getHeight() - 16) / 2);
                    }
                };
                b.setContentAreaFilled(false);
                b.setBorderPainted(false);
                b.setFocusPainted(false);
                b.setOpaque(false);
                b.setPreferredSize(new Dimension(30, 30));
                return b;
            }
            @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }
            @Override protected ComboPopup createPopup() {
                BasicComboPopup p = new BasicComboPopup(comboBox) {
                    @Override protected JScrollPane createScroller() {
                        JScrollPane sp = super.createScroller();
                        Scroll.style(sp);
                        return sp;
                    }
                };
                p.setBorder(BorderFactory.createLineBorder(Theme.BORDER_HI));
                p.getList().setSelectionBackground(Theme.SURFACE_3);
                p.getList().setBackground(Theme.SURFACE_2);
                return p;
            }
        });
        setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, false);
                l.setBorder(new EmptyBorder(8, 12, 8, 12));
                l.setFont(Theme.font(13.5f));
                l.setForeground(Theme.TEXT);
                l.setBackground(sel ? Theme.SURFACE_3 : Theme.SURFACE_2);
                l.setOpaque(index >= 0);
                return l;
            }
        });
        setPreferredSize(new Dimension(180, 44));
    }

    @SuppressWarnings("unchecked")
    public T value() { return (T) getSelectedItem(); }

    public void setItems(List<T> items) {
        Object sel = getSelectedItem();
        setModel(new DefaultComboBoxModel<>(new java.util.Vector<>(items)));
        if (sel != null && items.contains(sel)) setSelectedItem(sel);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.g2(g);
        int w = getWidth(), h = getHeight();
        g2.setColor(isEnabled() ? Theme.SURFACE_2 : Theme.SURFACE);
        g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 12, 12));
        g2.setColor(isPopupVisible() || hasFocus() ? Theme.ACCENT : Theme.BORDER);
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 12, 12));
        g2.dispose();
        super.paintComponent(g);
    }
}
