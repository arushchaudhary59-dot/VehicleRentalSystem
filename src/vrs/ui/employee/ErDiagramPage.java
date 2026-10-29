package vrs.ui.employee;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import vrs.service.SchemaService;
import vrs.service.SchemaService.*;
import vrs.ui.*;
import vrs.ui.comp.*;

/**
 * Interactive Chen ER diagram of the database.
 *  - scroll to zoom, drag the background to pan, drag entities / relationships to rearrange
 *  - hover an entity to highlight its attributes and relationships
 *  - click an entity to see the live table structure and row count from MySQL
 */
final class ErDiagramPage extends JPanel implements EmployeeShell.Refreshable {
    private final Canvas canvas = new Canvas();
    private final JPanel side = Ui.transparent(new BorderLayout());
    private final Chip attrs = new Chip("Attributes").icon("eye");
    private final JLabel zoomLabel = Ui.label("100%", Theme.bold(12.5f), Theme.TEXT_2);
    private Map<String, Long> counts = new HashMap<>();
    private boolean fitted;

    ErDiagramPage(EmployeeShell shell) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(EmployeeShell.pagePad());
        attrs.setSelected(true);
        attrs.addActionListener(e -> canvas.repaint());

        Btn zoomOut = new Btn("", Btn.Kind.SECONDARY, "zoomOut").small().tip("Zoom out");
        Btn zoomIn = new Btn("", Btn.Kind.SECONDARY, "zoomIn").small().tip("Zoom in");
        Btn fit = new Btn("Fit", Btn.Kind.SECONDARY, "fit").small();
        Btn reset = new Btn("Reset layout", Btn.Kind.SECONDARY, "refresh").small();
        Btn export = new Btn("Export PNG", Btn.Kind.SECONDARY, "download").small();
        zoomOut.addActionListener(e -> canvas.zoomBy(1 / 1.2, null));
        zoomIn.addActionListener(e -> canvas.zoomBy(1.2, null));
        fit.addActionListener(e -> canvas.fit());
        reset.addActionListener(e -> { canvas.resetLayout(); canvas.fit(); });
        export.addActionListener(e -> exportPng());
        zoomLabel.setPreferredSize(new Dimension(46, 30));
        zoomLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel top = Ui.transparent(new BorderLayout());
        top.add(EmployeeShell.header("ER Diagram", "Chen notation of vehicle_rental_db. Hover, drag, zoom and click an entity for live details."), BorderLayout.NORTH);
        JPanel tools = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.setBorder(BorderFactory.createEmptyBorder(14, -8, 14, 0));
        tools.add(zoomOut); tools.add(zoomLabel); tools.add(zoomIn); tools.add(fit);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(attrs); tools.add(reset); tools.add(export);
        tools.add(Box.createHorizontalStrut(16));
        tools.add(legend());
        top.add(tools, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        Card canvasCard = new Card(new BorderLayout()).fill(canvasBg()).pad(1, 1, 1, 1);
        canvasCard.add(canvas);
        JPanel center = Ui.transparent(new BorderLayout(16, 0));
        center.add(canvasCard, BorderLayout.CENTER);
        side.setPreferredSize(new Dimension(300, 10));
        center.add(side, BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
        showPlaceholder();
    }

    @Override public void refresh() {
        if (!fitted) SwingUtilities.invokeLater(() -> { canvas.fit(); fitted = true; });
        Async.run(SchemaService::rowCounts, c -> { counts = c; canvas.repaint(); });
    }

    private JComponent legend() {
        JComponent l = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                g2.setFont(Theme.font(12));
                int x = 0, y = 8;
                g2.setColor(entFill()); g2.fill(new Rectangle2D.Double(x, y, 22, 14));
                g2.setColor(entLine()); g2.draw(new Rectangle2D.Double(x, y, 22, 14));
                g2.setColor(Theme.TEXT_2); g2.drawString("Entity", x + 28, y + 12); x += 82;
                Path2D d = new Path2D.Double(); d.moveTo(x + 11, y - 2); d.lineTo(x + 24, y + 7); d.lineTo(x + 11, y + 16); d.lineTo(x - 2, y + 7); d.closePath();
                g2.setColor(relFill()); g2.fill(d); g2.setColor(relLine()); g2.draw(d);
                g2.setColor(Theme.TEXT_2); g2.drawString("Relationship", x + 30, y + 12); x += 122;
                g2.setColor(attLine()); g2.draw(new Ellipse2D.Double(x, y, 26, 14));
                g2.setColor(Theme.TEXT_2); g2.drawString("Attribute", x + 32, y + 12); x += 96;
                g2.setColor(Theme.TEXT); g2.setFont(Theme.bold(12)); g2.drawString("Key", x, y + 12);
                g2.drawLine(x, y + 14, x + g2.getFontMetrics().stringWidth("Key"), y + 14);
                g2.dispose();
            }
        };
        l.setPreferredSize(new Dimension(360, 30));
        return l;
    }

    // ------------------------------------------------------------------ side panel
    private void showPlaceholder() {
        side.removeAll();
        Card c = new Card(new BorderLayout()).pad(20);
        c.add(Widgets.empty("diagram", "Click an entity", "to see its live columns, keys and rows."));
        side.add(c);
        side.revalidate();
        side.repaint();
    }

    private void showEntity(ErModel.Entity e) {
        side.removeAll();
        Card c = new Card(new BorderLayout(0, 12)).pad(18, 18, 14, 18);
        JLabel title = Ui.label(e.table(), Theme.bold(18), Theme.TEXT);
        JLabel desc = Ui.label("<html><body style='width:190px'>" + e.description() + "</body></html>", Theme.font(12.5f), Theme.TEXT_2);
        JLabel rows = Ui.label("Loading…", Theme.bold(12.5f), Theme.ACCENT_HI);
        rows.setIcon(Icons.of("db", 14, Theme.ACCENT_HI));
        rows.setIconTextGap(6);
        c.add(Ui.col(6, title, desc, rows), BorderLayout.NORTH);

        JPanel body = Ui.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(Ui.left(Ui.label("RELATIONSHIPS", Theme.bold(10.5f), Theme.TEXT_3)));
        body.add(Box.createVerticalStrut(6));
        for (ErModel.Rel r : ErModel.RELS) {
            if (!r.a().equals(e.id()) && !r.b().equals(e.id())) continue;
            String other = r.a().equals(e.id()) ? name(r.b()) : name(r.a());
            String line = name(r.a()) + " " + r.cardA() + " — " + r.label() + " — " + r.cardB() + " " + name(r.b());
            JLabel l = Ui.label("<html><body style='width:200px'><font color='" + Theme.hex(Theme.TEXT) + "'>" + r.label() + "</font> " + other
                    + "<br><font color='" + Theme.hex(Theme.TEXT_3) + "'>" + line + "</font></body></html>", Theme.font(12), Theme.TEXT_2);
            body.add(Ui.left(l));
            body.add(Box.createVerticalStrut(6));
        }
        body.add(Box.createVerticalStrut(8));
        body.add(Ui.left(Ui.label("COLUMNS (LIVE FROM MYSQL)", Theme.bold(10.5f), Theme.TEXT_3)));
        body.add(Box.createVerticalStrut(6));
        JPanel cols = Ui.transparent(null);
        cols.setLayout(new BoxLayout(cols, BoxLayout.Y_AXIS));
        cols.add(Ui.left(Ui.label("Loading…", Theme.font(12), Theme.TEXT_3)));
        body.add(cols);
        Page page = new Page();
        page.add(body, 0);
        c.add(Scroll.of(page), BorderLayout.CENTER);
        side.add(c);
        side.revalidate();
        side.repaint();

        Async.run(() -> SchemaService.describe(e.table()), info -> {
            rows.setText(String.format("%,d rows", info.rows()));
            cols.removeAll();
            for (Column col : info.columns()) {
                JPanel r = Ui.transparent(new BorderLayout(8, 0));
                r.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
                JLabel n = Ui.label(col.name(), col.key().equals("PK") ? Theme.bold(12.5f) : Theme.font(12.5f), Theme.TEXT);
                if (!col.key().isEmpty()) {
                    Color kc = col.key().equals("PK") ? Theme.WARN : col.key().equals("FK") ? Theme.INFO : Theme.VIOLET;
                    n.setIcon(keyIcon(col.key(), kc));
                    n.setIconTextGap(6);
                }
                if (col.references() != null) n.setToolTipText("References " + col.references());
                r.add(n, BorderLayout.WEST);
                JLabel t = Ui.label(col.type(), Theme.mono(11), Theme.TEXT_3);
                t.setToolTipText(col.nullable() ? "NULL allowed" : "NOT NULL");
                r.add(t, BorderLayout.EAST);
                r.setAlignmentX(0f);
                r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                cols.add(r);
            }
            cols.revalidate();
            cols.repaint();
        });
    }

    private static Icon keyIcon(String key, Color c) {
        return new Icon() {
            public int getIconWidth() { return 26; }
            public int getIconHeight() { return 16; }
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = Theme.g2(g);
                g2.setColor(Theme.alpha(c, 40));
                g2.fill(new RoundRectangle2D.Double(x, y, 26, 16, 6, 6));
                g2.setColor(c);
                g2.setFont(Theme.bold(9.5f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(key, x + (26 - fm.stringWidth(key)) / 2, y + 12);
                g2.dispose();
            }
        };
    }

    private static String name(String id) {
        for (ErModel.Entity e : ErModel.ENTITIES) if (e.id().equals(id)) return e.table();
        return id;
    }

    private void exportPng() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("Vehicle_Rental_ER_Diagram.png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Rectangle2D b = canvas.diagramBounds();
            int w = (int) b.getWidth() + 80, h = (int) b.getHeight() + 80;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = Theme.g2(img.getGraphics());
            g2.setColor(canvasBg());
            g2.fillRect(0, 0, w, h);
            g2.translate(40 - b.getX(), 40 - b.getY());
            canvas.draw(g2, true);
            g2.dispose();
            ImageIO.write(img, "png", fc.getSelectedFile());
            Toast.success("Saved " + fc.getSelectedFile().getName());
        } catch (Exception ex) {
            Toast.error("Could not save the image: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ the canvas
    // diagram colours follow the dark / light theme
    static Color canvasBg() { return Theme.isLight() ? new Color(0xFAFBFC) : new Color(0x0D1218); }
    static Color entFill() { return Theme.isLight() ? new Color(0xE8F1F7) : new Color(0x15212E); }
    static Color entLine() { return Theme.isLight() ? new Color(0x2E5C78) : new Color(0x7FB2CE); }
    static Color relFill() { return Theme.isLight() ? new Color(0xE3F4EA) : new Color(0x10231C); }
    static Color relLine() { return Theme.isLight() ? new Color(0x1E8A5E) : new Color(0x34B886); }
    static Color attFill() { return Theme.isLight() ? Color.WHITE : new Color(0x0F161E); }
    static Color attLine() { return Theme.isLight() ? new Color(0x8A9BAC) : new Color(0x4F6577); }
    static Color edge() { return Theme.isLight() ? new Color(0x9AA8B6) : new Color(0x4A5B6C); }

    private final class Canvas extends JComponent {
        private final Map<String, Point2D.Double> pos = new HashMap<>();     // entity + relationship top-left
        private final Map<ErModel.Attr, Point2D.Double> attrPos = new HashMap<>();
        private double scale = 0.5, tx = 20, ty = 20;
        private String hoverEntity, selectedEntity;
        private Object dragging;            // entity id / Rel / null (= panning)
        private Point lastMouse;

        Canvas() {
            resetLayout();
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    lastMouse = e.getPoint();
                    Point2D w = toWorld(e.getPoint());
                    dragging = hit(w);
                    if (dragging == null) setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                public void mouseReleased(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(hoverEntity != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    dragging = null;
                }
                public void mouseClicked(MouseEvent e) {
                    Object h = hit(toWorld(e.getPoint()));
                    if (h instanceof String id) {
                        selectedEntity = id;
                        for (ErModel.Entity en : ErModel.ENTITIES) if (en.id().equals(id)) showEntity(en);
                    } else if (h == null) { selectedEntity = null; showPlaceholder(); }
                    repaint();
                }
                public void mouseDragged(MouseEvent e) {
                    double dx = e.getX() - lastMouse.x, dy = e.getY() - lastMouse.y;
                    lastMouse = e.getPoint();
                    if (dragging == null) { tx += dx; ty += dy; }
                    else {
                        String key = dragging instanceof String s ? s : ((ErModel.Rel) dragging).label();
                        Point2D.Double p = pos.get(key);
                        p.x += dx / scale; p.y += dy / scale;
                        if (dragging instanceof String id)                       // attributes follow their entity
                            for (var en : attrPos.entrySet()) if (en.getKey().owner().equals(id)) { en.getValue().x += dx / scale; en.getValue().y += dy / scale; }
                    }
                    repaint();
                }
                public void mouseMoved(MouseEvent e) {
                    Object h = hit(toWorld(e.getPoint()));
                    String he = h instanceof String s ? s : null;
                    setCursor(Cursor.getPredefinedCursor(h != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    if (!Objects.equals(he, hoverEntity)) { hoverEntity = he; repaint(); }
                }
                public void mouseExited(MouseEvent e) { hoverEntity = null; repaint(); }
                public void mouseWheelMoved(MouseWheelEvent e) { zoomBy(Math.pow(1.1, -e.getPreciseWheelRotation()), e.getPoint()); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        void resetLayout() {
            pos.clear();
            attrPos.clear();
            for (ErModel.Entity e : ErModel.ENTITIES) pos.put(e.id(), new Point2D.Double(e.x(), e.y()));
            for (ErModel.Rel r : ErModel.RELS) pos.put(r.label(), new Point2D.Double(r.x(), r.y()));
            for (ErModel.Attr a : ErModel.ATTRS) attrPos.put(a, new Point2D.Double(a.x(), a.y()));
            repaint();
        }

        Rectangle2D diagramBounds() {
            Rectangle2D b = null;
            for (var p : pos.values()) b = union(b, new Rectangle2D.Double(p.x, p.y, ErModel.EW, ErModel.RH));
            if (attrs.isSelected()) for (var en : attrPos.entrySet())
                b = union(b, new Rectangle2D.Double(en.getValue().x, en.getValue().y, en.getKey().w(), ErModel.AH));
            return b;
        }

        private Rectangle2D union(Rectangle2D a, Rectangle2D b) { if (a == null) return b; Rectangle2D r = a.getBounds2D(); r.add(b); return r; }

        void fit() {
            if (getWidth() < 10) return;
            Rectangle2D b = diagramBounds();
            scale = Math.min((getWidth() - 40) / b.getWidth(), (getHeight() - 40) / b.getHeight());
            scale = Math.max(0.15, Math.min(2.5, scale));
            tx = (getWidth() - b.getWidth() * scale) / 2 - b.getX() * scale;
            ty = (getHeight() - b.getHeight() * scale) / 2 - b.getY() * scale;
            zoomLabel.setText(Math.round(scale * 100) + "%");
            repaint();
        }

        void zoomBy(double f, Point anchor) {
            Point a = anchor != null ? anchor : new Point(getWidth() / 2, getHeight() / 2);
            double ns = Math.max(0.15, Math.min(2.5, scale * f));
            tx = a.x - (a.x - tx) * (ns / scale);
            ty = a.y - (a.y - ty) * (ns / scale);
            scale = ns;
            zoomLabel.setText(Math.round(scale * 100) + "%");
            repaint();
        }

        Point2D toWorld(Point p) { return new Point2D.Double((p.x - tx) / scale, (p.y - ty) / scale); }

        /** Entity id (String), relationship (Rel) or null. */
        Object hit(Point2D w) {
            for (ErModel.Entity e : ErModel.ENTITIES) if (entityRect(e.id()).contains(w)) return e.id();
            for (ErModel.Rel r : ErModel.RELS) if (diamond(r).contains(w)) return r;
            return null;
        }

        Rectangle2D entityRect(String id) { Point2D.Double p = pos.get(id); return new Rectangle2D.Double(p.x, p.y, ErModel.EW, ErModel.EH); }

        Shape diamond(ErModel.Rel r) {
            Point2D.Double p = pos.get(r.label());
            Path2D d = new Path2D.Double();
            d.moveTo(p.x + ErModel.RW / 2, p.y); d.lineTo(p.x + ErModel.RW, p.y + ErModel.RH / 2);
            d.lineTo(p.x + ErModel.RW / 2, p.y + ErModel.RH); d.lineTo(p.x, p.y + ErModel.RH / 2); d.closePath();
            return d;
        }

        Point2D center(String id) { Rectangle2D r = entityRect(id); return new Point2D.Double(r.getCenterX(), r.getCenterY()); }
        Point2D relCenter(ErModel.Rel r) { Point2D.Double p = pos.get(r.label()); return new Point2D.Double(p.x + ErModel.RW / 2, p.y + ErModel.RH / 2); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            g2.setColor(canvasBg());
            g2.fillRect(0, 0, getWidth(), getHeight());
            double step = 40 * scale;                                            // dot grid
            if (step > 8) {
                g2.setColor(Theme.alpha(Theme.TEXT, 14));
                for (double x = tx % step; x < getWidth(); x += step)
                    for (double y = ty % step; y < getHeight(); y += step) g2.fill(new Rectangle2D.Double(x, y, 1.5, 1.5));
            }
            g2.translate(tx, ty);
            g2.scale(scale, scale);
            draw(g2, false);
            g2.dispose();
        }

        /** Draws the diagram in world coordinates. */
        void draw(Graphics2D g2, boolean export) {
            String focus = export ? null : hoverEntity != null ? hoverEntity : selectedEntity;
            Set<String> near = new HashSet<>();
            if (focus != null) {
                near.add(focus);
                for (ErModel.Rel r : ErModel.RELS)
                    if (r.a().equals(focus) || r.b().equals(focus)) { near.add(r.a()); near.add(r.b()); near.add(r.label()); }
            }
            boolean showAttrs = attrs.isSelected();
            float faded = 0.22f;

            // edges: attribute links
            g2.setStroke(new BasicStroke(1.6f));
            if (showAttrs) for (var en : attrPos.entrySet()) {
                ErModel.Attr a = en.getKey();
                boolean on = focus == null || a.owner().equals(focus);
                g2.setColor(on && focus != null ? Theme.mix(edge(), entLine(), 0.5) : Theme.alpha(edge(), on ? 255 : (int) (255 * faded)));
                Point2D c = center(a.owner());
                g2.draw(new Line2D.Double(c.getX(), c.getY(), en.getValue().x + a.w() / 2, en.getValue().y + ErModel.AH / 2));
            }
            // edges: relationships
            for (ErModel.Rel r : ErModel.RELS) {
                boolean on = focus == null || near.contains(r.label());
                Point2D rc = relCenter(r);
                for (int side = 0; side < 2; side++) {
                    String ent = side == 0 ? r.a() : r.b();
                    Point2D ec = center(ent);
                    g2.setColor(on && focus != null ? Theme.ACCENT : Theme.alpha(edge(), on ? 255 : (int) (255 * faded)));
                    g2.setStroke(new BasicStroke(on && focus != null ? 2.4f : 1.8f));
                    g2.draw(new Line2D.Double(ec.getX(), ec.getY(), rc.getX(), rc.getY()));
                }
            }
            // attributes
            if (showAttrs) for (var en : attrPos.entrySet()) {
                ErModel.Attr a = en.getKey();
                boolean on = focus == null || a.owner().equals(focus);
                Composite old = g2.getComposite();
                if (!on) g2.setComposite(AlphaComposite.SrcOver.derive(faded));
                Ellipse2D e = new Ellipse2D.Double(en.getValue().x, en.getValue().y, a.w(), ErModel.AH);
                g2.setColor(attFill()); g2.fill(e);
                g2.setColor(on && focus != null ? entLine() : attLine());
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(e);
                g2.setFont(a.pk() ? Theme.bold(14) : Theme.font(14));
                g2.setColor(a.pk() ? Theme.TEXT : Theme.TEXT_2);
                FontMetrics fm = g2.getFontMetrics();
                float x = (float) (e.getCenterX() - fm.stringWidth(a.label()) / 2.0), y = (float) (e.getCenterY() + fm.getAscent() / 2.0 - 2);
                g2.drawString(a.label(), x, y);
                if (a.pk()) g2.draw(new Line2D.Double(x, y + 3, x + fm.stringWidth(a.label()), y + 3));
                g2.setComposite(old);
            }
            // relationships
            for (ErModel.Rel r : ErModel.RELS) {
                boolean on = focus == null || near.contains(r.label());
                Composite old = g2.getComposite();
                if (!on) g2.setComposite(AlphaComposite.SrcOver.derive(faded));
                Shape d = diamond(r);
                g2.setColor(relFill()); g2.fill(d);
                g2.setColor(on && focus != null ? Theme.ACCENT_HI : relLine());
                g2.setStroke(new BasicStroke(2f));
                g2.draw(d);
                Point2D c = relCenter(r);
                g2.setFont(Theme.bold(13));
                g2.setColor(Theme.TEXT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(r.label(), (float) (c.getX() - fm.stringWidth(r.label()) / 2.0), (float) (c.getY() + 5));
                // cardinality labels half way along each edge
                for (int side = 0; side < 2; side++) {
                    Point2D ec = center(side == 0 ? r.a() : r.b());
                    String card = side == 0 ? r.cardA() : r.cardB();
                    double mx = (ec.getX() + c.getX()) / 2, my = (ec.getY() + c.getY()) / 2;
                    g2.setColor(canvasBg());
                    g2.fill(new RoundRectangle2D.Double(mx - 13, my - 12, 26, 24, 10, 10));
                    g2.setColor(on && focus != null ? Theme.ACCENT_HI : Theme.WARN);
                    g2.setFont(Theme.bold(15));
                    FontMetrics f2 = g2.getFontMetrics();
                    g2.drawString(card, (float) (mx - f2.stringWidth(card) / 2.0), (float) (my + 5.5));
                }
                g2.setComposite(old);
            }
            // entities
            for (ErModel.Entity e : ErModel.ENTITIES) {
                boolean on = focus == null || near.contains(e.id());
                Composite old = g2.getComposite();
                if (!on) g2.setComposite(AlphaComposite.SrcOver.derive(faded));
                Rectangle2D r = entityRect(e.id());
                if (e.id().equals(focus) || e.id().equals(selectedEntity) && !export) {
                    g2.setColor(Theme.alpha(Theme.ACCENT, 40));
                    g2.fill(new RoundRectangle2D.Double(r.getX() - 8, r.getY() - 8, r.getWidth() + 16, r.getHeight() + 16, 14, 14));
                }
                g2.setColor(entFill()); g2.fill(r);
                g2.setColor(e.id().equals(focus) ? Theme.ACCENT_HI : entLine());
                g2.setStroke(new BasicStroke(2.2f));
                g2.draw(r);
                g2.setFont(Theme.bold(17));
                g2.setColor(Theme.TEXT);
                FontMetrics fm = g2.getFontMetrics();
                Long n = counts.get(e.table());
                double cy = r.getCenterY() + (n == null || export ? 6 : -1);
                g2.drawString(e.table(), (float) (r.getCenterX() - fm.stringWidth(e.table()) / 2.0), (float) cy);
                if (n != null && !export) {
                    String s = String.format("%,d rows", n);
                    g2.setFont(Theme.font(12));
                    g2.setColor(Theme.TEXT_3);
                    g2.drawString(s, (float) (r.getCenterX() - g2.getFontMetrics().stringWidth(s) / 2.0), (float) (cy + 18));
                }
                g2.setComposite(old);
            }
        }
    }
}
