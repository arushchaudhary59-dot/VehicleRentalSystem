package vrs.ui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.Icon;

/**
 * A distinct symbol for every vehicle type (used instead of photos):
 * Hatchback, Sedan, SUV, MUV, Cruiser, Sports, Commuter, Scooter.
 * Drawn as side views on a 120 x 64 canvas and scaled to any size.
 */
public final class VehicleIcons {
    private VehicleIcons() {}

    public static Icon of(String bodyType, int width, boolean electric) {
        return of(bodyType, width, Theme.typeColor(bodyType), electric);
    }

    public static Icon of(String bodyType, int width, Color color, boolean electric) {
        int height = (int) Math.round(width * 64 / 120.0);
        return new Icon() {
            public int getIconWidth() { return width; }
            public int getIconHeight() { return height; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = Theme.g2(g);
                g2.translate(x, y);
                g2.scale(width / 120.0, height / 64.0);
                paint(g2, bodyType, color, electric);
                g2.dispose();
            }
        };
    }

    /** Paints on a 120 x 64 canvas. */
    public static void paint(Graphics2D g, String type, Color col, boolean electric) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new Ellipse2D.Double(8, 55, 104, 6));                       // ground shadow
        switch (type == null ? "" : type) {
            case "Hatchback" -> hatchback(g, col);
            case "Sedan" -> sedan(g, col);
            case "SUV" -> suv(g, col);
            case "MUV" -> muv(g, col);
            case "Cruiser" -> cruiser(g, col);
            case "Sports" -> sports(g, col);
            case "Commuter" -> commuter(g, col);
            case "Scooter" -> scooter(g, col);
            default -> hatchback(g, col);
        }
        if (electric) {
            g.setColor(Theme.ACCENT);
            g.fill(new Ellipse2D.Double(100, 0, 18, 18));
            g.setColor(Theme.ACCENT_INK);
            Path2D bolt = new Path2D.Double();
            bolt.moveTo(110.5, 2.5); bolt.lineTo(104.5, 10); bolt.lineTo(109, 10); bolt.lineTo(107.5, 15.5);
            bolt.lineTo(113.5, 7.5); bolt.lineTo(109, 7.5); bolt.closePath();
            g.fill(bolt);
        }
    }

    // ------------------------------------------------------------------ cars
    private static void carBody(Graphics2D g, Shape body, Color col) {
        g.setPaint(new GradientPaint(0, 8, Theme.mix(col, Color.WHITE, 0.25), 0, 50, Theme.mix(col, Color.BLACK, 0.25)));
        g.fill(body);
        g.setColor(Theme.mix(col, Color.WHITE, 0.45));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(body);
    }

    private static void glass(Graphics2D g, Shape s) {
        g.setPaint(new GradientPaint(0, 10, new Color(0x9FC3E6), 0, 32, new Color(0x2A3F57)));
        g.fill(s);
    }

    private static void wheel(Graphics2D g, double cx, double cy, double r) {
        g.setColor(Theme.BG);                                              // arch gap
        g.fill(new Ellipse2D.Double(cx - r - 2.2, cy - r - 2.2, 2 * r + 4.4, 2 * r + 4.4));
        g.setColor(new Color(0x0D1117));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(new Color(0xB8C4D2));
        g.fill(new Ellipse2D.Double(cx - r * 0.5, cy - r * 0.5, r, r));
        g.setColor(new Color(0x56657A));
        g.fill(new Ellipse2D.Double(cx - r * 0.18, cy - r * 0.18, r * 0.36, r * 0.36));
    }

    private static void lights(Graphics2D g, double fx, double fy, double rx, double ry) {
        g.setColor(new Color(0xFFF2B0));
        g.fill(new RoundRectangle2D.Double(fx, fy, 5, 3, 2, 2));
        g.setColor(new Color(0xFF5A5A));
        g.fill(new RoundRectangle2D.Double(rx, ry, 3.5, 4, 2, 2));
    }

    private static void hatchback(Graphics2D g, Color c) {
        Path2D b = new Path2D.Double();
        b.moveTo(11, 47); b.lineTo(9, 40); b.quadTo(9, 34, 15, 33); b.lineTo(36, 30);
        b.quadTo(45, 18, 56, 15.5); b.lineTo(80, 15.5); b.quadTo(91, 16, 101, 27);
        b.quadTo(106, 32, 106, 40); b.lineTo(104, 47); b.closePath();
        carBody(g, b, c);
        Path2D w = new Path2D.Double();
        w.moveTo(42, 30); w.quadTo(48, 21, 57, 19); w.lineTo(79, 19); w.quadTo(87, 19.5, 95, 28.5); w.lineTo(95, 30); w.closePath();
        glass(g, w);
        g.setColor(Theme.mix(c, Color.BLACK, 0.1)); g.fill(new Rectangle2D.Double(67, 18, 3, 13));
        lights(g, 10, 35, 102, 31);
        wheel(g, 30, 47, 9.5); wheel(g, 87, 47, 9.5);
    }

    private static void sedan(Graphics2D g, Color c) {
        Path2D b = new Path2D.Double();
        b.moveTo(7, 47); b.lineTo(5, 40); b.quadTo(5, 34, 11, 33); b.lineTo(33, 30.5);
        b.quadTo(43, 19, 55, 16.5); b.lineTo(76, 16.5); b.quadTo(87, 17.5, 95, 28.5);
        b.lineTo(110, 30.5); b.quadTo(115, 31.5, 115, 37); b.lineTo(113, 47); b.closePath();
        carBody(g, b, c);
        Path2D w = new Path2D.Double();
        w.moveTo(39, 30.5); w.quadTo(46, 22, 56.5, 20); w.lineTo(75, 20); w.quadTo(83, 20.5, 89.5, 29.5); w.closePath();
        glass(g, w);
        g.setColor(Theme.mix(c, Color.BLACK, 0.1)); g.fill(new Rectangle2D.Double(65, 19, 3, 12));
        lights(g, 6, 35, 111, 32);
        wheel(g, 27, 47, 9.5); wheel(g, 94, 47, 9.5);
    }

    private static void suv(Graphics2D g, Color c) {
        Path2D b = new Path2D.Double();
        b.moveTo(9, 47); b.lineTo(7, 36); b.quadTo(7, 29.5, 13, 28.5); b.lineTo(31, 26.5);
        b.lineTo(41, 12.5); b.quadTo(43, 10, 47, 10); b.lineTo(103, 10); b.quadTo(108.5, 10, 109.5, 16);
        b.lineTo(112, 30); b.lineTo(112, 47); b.closePath();
        carBody(g, b, c);
        g.setColor(Theme.mix(c, Color.WHITE, 0.5)); g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(50, 6.5, 99, 6.5)); g.draw(new Line2D.Double(54, 6.5, 54, 10)); g.draw(new Line2D.Double(95, 6.5, 95, 10));
        Path2D w1 = new Path2D.Double(); w1.moveTo(37, 26.5); w1.lineTo(45, 14.5); w1.lineTo(63, 14.5); w1.lineTo(63, 26.5); w1.closePath();
        glass(g, w1);
        glass(g, new Rectangle2D.Double(67, 14.5, 18, 12));
        Path2D w3 = new Path2D.Double(); w3.moveTo(89, 14.5); w3.lineTo(102, 14.5); w3.lineTo(105, 26.5); w3.lineTo(89, 26.5); w3.closePath();
        glass(g, w3);
        g.setColor(new Color(0x2B3442)); g.fill(new RoundRectangle2D.Double(6, 41, 106, 4, 2, 2));   // cladding
        lights(g, 8, 31, 108.5, 29);
        wheel(g, 30, 46, 11); wheel(g, 90, 46, 11);
    }

    private static void muv(Graphics2D g, Color c) {
        Path2D b = new Path2D.Double();
        b.moveTo(6, 47); b.lineTo(4, 38); b.quadTo(4, 31.5, 10, 30.5); b.lineTo(24, 28.5);
        b.quadTo(34, 14, 45, 12); b.lineTo(108, 12); b.quadTo(114, 12, 114.5, 18); b.lineTo(116, 47); b.closePath();
        carBody(g, b, c);
        Path2D w1 = new Path2D.Double(); w1.moveTo(31, 28.5); w1.quadTo(38, 17.5, 46, 15.5); w1.lineTo(59, 15.5); w1.lineTo(59, 28.5); w1.closePath();
        glass(g, w1);
        glass(g, new Rectangle2D.Double(63, 15.5, 19, 13));
        Path2D w3 = new Path2D.Double(); w3.moveTo(86, 15.5); w3.lineTo(107, 15.5); w3.lineTo(109, 28.5); w3.lineTo(86, 28.5); w3.closePath();
        glass(g, w3);
        lights(g, 5, 34, 112.5, 31);
        wheel(g, 26, 47, 10); wheel(g, 94, 47, 10);
    }

    // ------------------------------------------------------------------ two-wheelers
    private static void bikeWheel(Graphics2D g, double cx, double cy, double r) {
        g.setColor(new Color(0x0D1117));
        g.setStroke(new BasicStroke(5.5f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(new Color(0x8794A6));
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new Ellipse2D.Double(cx - r + 3.6, cy - r + 3.6, 2 * r - 7.2, 2 * r - 7.2));
        g.setColor(new Color(0xB8C4D2));
        g.fill(new Ellipse2D.Double(cx - 2.6, cy - 2.6, 5.2, 5.2));
    }

    private static void stroke(Graphics2D g, Color c, float w) {
        g.setColor(c);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    private static Paint bodyPaint(Color c) {
        return new GradientPaint(0, 12, Theme.mix(c, Color.WHITE, 0.3), 0, 36, Theme.mix(c, Color.BLACK, 0.2));
    }

    private static void engine(Graphics2D g, double x, double y, double w, double h) {
        g.setColor(new Color(0x4A5566));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 5, 5));
        g.setColor(new Color(0x6E7C90));
        g.setStroke(new BasicStroke(1f));
        for (double yy = y + 3; yy < y + h - 2; yy += 3) g.draw(new Line2D.Double(x + 2, yy, x + w - 2, yy));
    }

    private static void cruiser(Graphics2D g, Color c) {
        Color metal = new Color(0x9AA6B5);
        bikeWheel(g, 24, 44, 14); bikeWheel(g, 96, 44, 14);
        stroke(g, new Color(0x3A4556), 3.5f);
        g.draw(new Line2D.Double(62, 44, 96, 44));                                   // swing arm
        g.draw(new Line2D.Double(84, 28, 96, 44));
        stroke(g, metal, 3f);
        g.draw(new Line2D.Double(24, 44, 38, 14));                                   // raked fork
        engine(g, 50, 30, 20, 13);
        stroke(g, new Color(0xC9D2DC), 2.6f);
        g.draw(new Line2D.Double(56, 46, 108, 43));                                   // exhaust
        Path2D tank = new Path2D.Double();
        tank.moveTo(44, 24); tank.quadTo(52, 13, 66, 15); tank.quadTo(74, 17, 72, 25); tank.lineTo(48, 28); tank.closePath();
        g.setPaint(bodyPaint(c)); g.fill(tank);
        Path2D seat = new Path2D.Double();
        seat.moveTo(70, 26); seat.quadTo(80, 20.5, 92, 24); seat.lineTo(90, 29); seat.lineTo(71, 29.5); seat.closePath();
        g.setColor(new Color(0x2B2F38)); g.fill(seat);
        Path2D fender = new Path2D.Double();
        fender.moveTo(84, 32); fender.quadTo(98, 24, 110, 36); fender.lineTo(107, 37); fender.quadTo(98, 29, 86, 34); fender.closePath();
        g.setPaint(bodyPaint(c)); g.fill(fender);
        stroke(g, metal, 2.6f);
        Path2D bar = new Path2D.Double(); bar.moveTo(38, 14); bar.quadTo(41, 6, 50, 8.5);
        g.draw(bar);
        g.setColor(new Color(0xFFF2B0)); g.fill(new Ellipse2D.Double(30, 15, 8, 8));
        g.setColor(metal); g.setStroke(new BasicStroke(1.2f)); g.draw(new Ellipse2D.Double(30, 15, 8, 8));
    }

    private static void sports(Graphics2D g, Color c) {
        bikeWheel(g, 24, 44, 14); bikeWheel(g, 96, 44, 14);
        stroke(g, new Color(0x3A4556), 3.5f);
        g.draw(new Line2D.Double(64, 40, 96, 44));
        stroke(g, new Color(0xC9A24A), 3f);
        g.draw(new Line2D.Double(24, 44, 36, 18));                                   // gold fork
        engine(g, 52, 31, 16, 11);
        Path2D fairing = new Path2D.Double();
        fairing.moveTo(30, 30); fairing.quadTo(31, 17, 44, 13); fairing.lineTo(58, 16); fairing.lineTo(70, 27);
        fairing.lineTo(64, 38); fairing.lineTo(46, 40); fairing.quadTo(36, 38, 30, 30); fairing.closePath();
        g.setPaint(bodyPaint(c)); g.fill(fairing);
        Path2D tail = new Path2D.Double();
        tail.moveTo(70, 27); tail.lineTo(86, 22); tail.lineTo(108, 15); tail.lineTo(104, 22); tail.lineTo(86, 30); tail.closePath();
        g.fill(tail);
        g.setColor(new Color(0x2B2F38));
        Path2D seat = new Path2D.Double(); seat.moveTo(64, 22); seat.lineTo(84, 19.5); seat.lineTo(86, 22); seat.lineTo(70, 26); seat.closePath();
        g.fill(seat);
        g.setColor(new Color(0x9FC3E6));
        Path2D screen = new Path2D.Double(); screen.moveTo(40, 14); screen.lineTo(46, 7); screen.lineTo(52, 10); screen.lineTo(47, 15); screen.closePath();
        g.fill(screen);
        g.setColor(Theme.mix(c, Color.BLACK, 0.45));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Line2D.Double(42, 30, 60, 30));
        g.setColor(new Color(0xFFF2B0)); g.fill(new RoundRectangle2D.Double(30, 22, 6, 3.5, 2, 2));
        stroke(g, new Color(0xC9D2DC), 2.4f);
        g.draw(new Line2D.Double(66, 42, 82, 36));
    }

    private static void commuter(Graphics2D g, Color c) {
        Color metal = new Color(0x9AA6B5);
        bikeWheel(g, 24, 44, 14); bikeWheel(g, 96, 44, 14);
        stroke(g, new Color(0x3A4556), 3.5f);
        g.draw(new Line2D.Double(62, 42, 96, 44));
        g.draw(new Line2D.Double(86, 26, 96, 44));
        stroke(g, metal, 3f);
        g.draw(new Line2D.Double(24, 44, 36, 13));
        stroke(g, new Color(0x3A4556), 3f);
        g.draw(new Line2D.Double(40, 20, 54, 36));                                   // down tube
        engine(g, 50, 32, 17, 11);
        Path2D tank = new Path2D.Double();
        tank.moveTo(40, 21); tank.lineTo(44, 16); tank.lineTo(64, 16.5); tank.lineTo(66, 24); tank.lineTo(46, 26); tank.closePath();
        g.setPaint(bodyPaint(c)); g.fill(tank);
        g.setColor(new Color(0x2B2F38));
        g.fill(new RoundRectangle2D.Double(63, 19, 30, 6, 5, 5));                   // long flat seat
        g.setPaint(bodyPaint(c));
        Path2D panel = new Path2D.Double(); panel.moveTo(66, 26); panel.lineTo(86, 25); panel.lineTo(80, 33); panel.lineTo(68, 33); panel.closePath();
        g.fill(panel);
        stroke(g, metal, 2.6f);
        g.draw(new Line2D.Double(36, 13, 32, 6)); g.draw(new Line2D.Double(30, 6, 44, 6.5));   // upright bar
        g.setColor(new Color(0xFFF2B0)); g.fill(new Ellipse2D.Double(28, 14, 7, 7));
        stroke(g, new Color(0xC9D2DC), 2.4f);
        g.draw(new Line2D.Double(58, 44, 100, 38));
    }

    private static void scooter(Graphics2D g, Color c) {
        Path2D body = new Path2D.Double();
        body.moveTo(34, 46); body.quadTo(28, 24, 37, 12); body.lineTo(45, 12);
        body.quadTo(40, 26, 47, 40); body.lineTo(70, 40); body.quadTo(73, 27, 90, 26);
        body.quadTo(108, 26, 112, 42); body.lineTo(106, 46); body.closePath();
        g.setPaint(new GradientPaint(0, 10, Theme.mix(c, Color.WHITE, 0.3), 0, 46, Theme.mix(c, Color.BLACK, 0.25)));
        g.fill(body);
        g.setColor(Theme.mix(c, Color.WHITE, 0.45)); g.setStroke(new BasicStroke(1.1f)); g.draw(body);
        g.setColor(new Color(0x2B2F38));
        Path2D seat = new Path2D.Double(); seat.moveTo(72, 25); seat.quadTo(86, 17, 102, 22); seat.lineTo(100, 26); seat.lineTo(74, 27.5); seat.closePath();
        g.fill(seat);
        g.setColor(new Color(0x3A4556)); g.fill(new RoundRectangle2D.Double(46, 39.5, 25, 4, 2, 2));   // floorboard
        stroke(g, new Color(0x9AA6B5), 2.6f);
        g.draw(new Line2D.Double(41, 12, 40, 6)); g.draw(new Line2D.Double(34, 6, 48, 6));
        g.setColor(new Color(0xFFF2B0)); g.fill(new RoundRectangle2D.Double(34, 14, 6, 4, 2, 2));
        bikeWheel(g, 30, 48, 9.5); bikeWheel(g, 98, 48, 9.5);
    }
}
