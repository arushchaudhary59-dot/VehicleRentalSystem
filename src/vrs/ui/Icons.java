package vrs.ui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.Icon;

/** Small line icons drawn with Java2D on a 24x24 grid (no image files needed). */
public final class Icons {
    private Icons() {}

    public static Icon of(String name, int size, Color color) {
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = Theme.g2(g);
                g2.translate(x, y);
                g2.scale(size / 24.0, size / 24.0);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                draw(name, g2);
                g2.dispose();
            }
        };
    }

    static void draw(String n, Graphics2D g) {
        switch (n) {
            case "search" -> { g.draw(new Ellipse2D.Double(4, 4, 13, 13)); g.draw(new Line2D.Double(15.5, 15.5, 20, 20)); }
            case "calendar" -> {
                g.draw(new RoundRectangle2D.Double(3.5, 5, 17, 15.5, 4, 4));
                g.draw(new Line2D.Double(3.5, 10, 20.5, 10));
                g.draw(new Line2D.Double(8, 3, 8, 7)); g.draw(new Line2D.Double(16, 3, 16, 7));
            }
            case "user" -> { g.draw(new Ellipse2D.Double(8, 3.5, 8, 8)); g.draw(new Arc2D.Double(4.5, 13.5, 15, 13, 0, 180, Arc2D.OPEN)); }
            case "logout" -> {
                g.draw(path(9, 4, 5, 4, 5, 20, 9, 20));
                g.draw(new Line2D.Double(10, 12, 20, 12)); g.draw(path(16, 8, 20, 12, 16, 16));
            }
            case "grid" -> {
                g.draw(new RoundRectangle2D.Double(4, 4, 7, 7, 2, 2)); g.draw(new RoundRectangle2D.Double(13, 4, 7, 7, 2, 2));
                g.draw(new RoundRectangle2D.Double(4, 13, 7, 7, 2, 2)); g.draw(new RoundRectangle2D.Double(13, 13, 7, 7, 2, 2));
            }
            case "clipboard" -> {
                g.draw(new RoundRectangle2D.Double(5, 5, 14, 16, 3, 3));
                g.draw(new RoundRectangle2D.Double(9, 3, 6, 4, 2, 2));
                g.draw(new Line2D.Double(9, 12, 15, 12)); g.draw(new Line2D.Double(9, 16, 13, 16));
            }
            case "card" -> {
                g.draw(new RoundRectangle2D.Double(3, 6, 18, 13, 3, 3));
                g.draw(new Line2D.Double(3, 10.5, 21, 10.5)); g.draw(new Line2D.Double(7, 15, 10, 15));
            }
            case "car" -> {
                g.draw(path(4, 16, 4, 12.5, 6.5, 7.5, 17.5, 7.5, 20, 12.5, 20, 16));
                g.draw(new Line2D.Double(4, 12.5, 20, 12.5));
                g.draw(new Ellipse2D.Double(6, 14.5, 4, 4)); g.draw(new Ellipse2D.Double(14, 14.5, 4, 4));
            }
            case "terminal" -> {
                g.draw(new RoundRectangle2D.Double(3, 4, 18, 16, 3, 3));
                g.draw(path(7, 9, 10, 12, 7, 15)); g.draw(new Line2D.Double(12, 15, 17, 15));
            }
            case "diagram" -> {
                g.draw(new RoundRectangle2D.Double(3, 3.5, 7, 5, 1.5, 1.5)); g.draw(new RoundRectangle2D.Double(14, 15.5, 7, 5, 1.5, 1.5));
                g.draw(path(17.5, 4, 21, 7.5, 17.5, 11, 14, 7.5, 17.5, 4));
                g.draw(new Line2D.Double(10, 6, 14, 7.5)); g.draw(new Line2D.Double(17.5, 11, 17.5, 15.5));
                g.draw(path(6.5, 8.5, 6.5, 18, 14, 18));
            }
            case "plus" -> { g.draw(new Line2D.Double(12, 5, 12, 19)); g.draw(new Line2D.Double(5, 12, 19, 12)); }
            case "refresh" -> {
                g.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 40, 280, Arc2D.OPEN));
                g.draw(path(18.5, 3.5, 18.5, 8, 14, 8));
            }
            case "check" -> g.draw(path(5, 12.5, 10, 17.5, 19, 7));
            case "x" -> { g.draw(new Line2D.Double(6, 6, 18, 18)); g.draw(new Line2D.Double(18, 6, 6, 18)); }
            case "edit" -> {
                g.draw(path(15, 5, 19, 9, 9, 19, 5, 19, 5, 15, 15, 5));
                g.draw(new Line2D.Double(13, 7, 17, 11));
            }
            case "trash" -> {
                g.draw(new Line2D.Double(4, 7, 20, 7)); g.draw(path(9, 7, 9, 4, 15, 4, 15, 7));
                g.draw(path(6, 7, 7, 20, 17, 20, 18, 7));
                g.draw(new Line2D.Double(10, 11, 10, 16)); g.draw(new Line2D.Double(14, 11, 14, 16));
            }
            case "chevLeft" -> g.draw(path(15, 5, 8, 12, 15, 19));
            case "chevRight" -> g.draw(path(9, 5, 16, 12, 9, 19));
            case "chevDown" -> g.draw(path(6, 9, 12, 15, 18, 9));
            case "arrowRight" -> { g.draw(new Line2D.Double(5, 12, 19, 12)); g.draw(path(13, 6, 19, 12, 13, 18)); }
            case "fuel" -> {
                g.draw(new RoundRectangle2D.Double(4, 4, 10, 16, 2, 2)); g.draw(new Line2D.Double(4, 10, 14, 10));
                g.draw(path(14, 8, 18, 10, 18, 17, 20, 17, 20, 9, 17, 6));
            }
            case "gear" -> {
                g.draw(new Ellipse2D.Double(4, 4, 4, 4)); g.draw(new Ellipse2D.Double(10, 4, 4, 4)); g.draw(new Ellipse2D.Double(16, 4, 4, 4));
                g.draw(new Line2D.Double(6, 8, 6, 20)); g.draw(new Line2D.Double(12, 8, 12, 20)); g.draw(new Line2D.Double(18, 8, 18, 14));
                g.draw(new Line2D.Double(6, 14, 18, 14));
            }
            case "seat" -> {
                g.draw(new Ellipse2D.Double(6, 3.5, 7, 7));
                g.draw(new Arc2D.Double(3, 13, 13, 12, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(14, 4, 6, 6, -90, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(15, 13, 6, 12, 0, 90, Arc2D.OPEN));
            }
            case "bolt" -> { g.fill(path(13, 2, 5, 13.5, 11, 13.5, 10, 22, 19, 9.5, 13, 9.5, 13, 2)); }
            case "filter" -> g.draw(path(4, 5, 20, 5, 14, 12.5, 14, 19, 10, 17, 10, 12.5, 4, 5));
            case "eye" -> {
                g.draw(path(2.5, 12, 7, 6.5, 12, 5, 17, 6.5, 21.5, 12, 17, 17.5, 12, 19, 7, 17.5, 2.5, 12));
                g.draw(new Ellipse2D.Double(9, 9, 6, 6));
            }
            case "eyeOff" -> {
                g.draw(path(2.5, 12, 7, 6.5, 12, 5, 17, 6.5, 21.5, 12, 17, 17.5, 12, 19, 7, 17.5, 2.5, 12));
                g.draw(new Line2D.Double(4, 4, 20, 20));
            }
            case "clock" -> { g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17)); g.draw(path(12, 7, 12, 12, 15.5, 14)); }
            case "zoomIn" -> { draw("search", g); g.draw(new Line2D.Double(10.5, 7.5, 10.5, 13.5)); g.draw(new Line2D.Double(7.5, 10.5, 13.5, 10.5)); }
            case "zoomOut" -> { draw("search", g); g.draw(new Line2D.Double(7.5, 10.5, 13.5, 10.5)); }
            case "fit" -> {
                g.draw(path(4, 9, 4, 4, 9, 4)); g.draw(path(15, 4, 20, 4, 20, 9));
                g.draw(path(20, 15, 20, 20, 15, 20)); g.draw(path(9, 20, 4, 20, 4, 15));
            }
            case "download" -> {
                g.draw(new Line2D.Double(12, 4, 12, 15)); g.draw(path(7, 10.5, 12, 15.5, 17, 10.5));
                g.draw(path(4, 16, 4, 20, 20, 20, 20, 16));
            }
            case "db" -> {
                g.draw(new Ellipse2D.Double(5, 3.5, 14, 5));
                g.draw(new Line2D.Double(5, 6, 5, 18)); g.draw(new Line2D.Double(19, 6, 19, 18));
                g.draw(new Arc2D.Double(5, 9.5, 14, 5, 180, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(5, 15.5, 14, 5, 180, 180, Arc2D.OPEN));
            }
            case "info" -> { g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17)); g.draw(new Line2D.Double(12, 11, 12, 16.5)); g.fill(new Ellipse2D.Double(10.8, 6.8, 2.4, 2.4)); }
            case "alert" -> {
                g.draw(path(12, 3.5, 21, 19.5, 3, 19.5, 12, 3.5));
                g.draw(new Line2D.Double(12, 9.5, 12, 13.5)); g.fill(new Ellipse2D.Double(10.9, 15.6, 2.2, 2.2));
            }
            case "mail" -> { g.draw(new RoundRectangle2D.Double(3, 5.5, 18, 13, 3, 3)); g.draw(path(4, 7, 12, 13, 20, 7)); }
            case "lock" -> { g.draw(new RoundRectangle2D.Double(4.5, 10.5, 15, 10, 3, 3)); g.draw(new Arc2D.Double(8, 3.5, 8, 13, 0, 180, Arc2D.OPEN)); g.draw(new Line2D.Double(8, 10, 8, 10.5)); g.draw(new Line2D.Double(16, 10, 16, 10.5)); }
            case "phone" -> g.draw(new RoundRectangle2D.Double(7, 3, 10, 18, 3, 3));
            case "id" -> {
                g.draw(new RoundRectangle2D.Double(3, 5.5, 18, 13, 3, 3));
                g.draw(new Ellipse2D.Double(6.5, 9, 4, 4)); g.draw(new Line2D.Double(5.5, 16, 11.5, 16));
                g.draw(new Line2D.Double(14, 10, 18, 10)); g.draw(new Line2D.Double(14, 14, 17, 14));
            }
            case "wallet" -> {
                g.draw(new RoundRectangle2D.Double(3, 6, 18, 14, 3, 3)); g.draw(path(17, 6, 17, 4, 5, 4, 3, 7));
                g.draw(new RoundRectangle2D.Double(14, 11, 7, 4.5, 2, 2));
            }
            case "trend" -> { g.draw(path(3, 17, 9, 11, 13, 14, 21, 6)); g.draw(path(15, 6, 21, 6, 21, 12)); }
            case "road" -> {
                g.draw(new Line2D.Double(8, 3, 4, 21)); g.draw(new Line2D.Double(16, 3, 20, 21));
                g.draw(new Line2D.Double(12, 4, 12, 7)); g.draw(new Line2D.Double(12, 10.5, 12, 13.5)); g.draw(new Line2D.Double(12, 17, 12, 20));
            }
            case "key" -> {
                g.draw(new Ellipse2D.Double(3, 10, 8, 8)); g.draw(new Line2D.Double(11, 14, 21, 14));
                g.draw(new Line2D.Double(17, 14, 17, 17.5)); g.draw(new Line2D.Double(20, 14, 20, 17));
            }
            case "sparkle" -> g.fill(path(12, 2, 14, 10, 22, 12, 14, 14, 12, 22, 10, 14, 2, 12, 10, 10, 12, 2));
            case "wrench" -> {
                g.draw(path(14.5, 6, 18, 2.5, 21.5, 6, 18, 9.5));
                g.draw(new Line2D.Double(16, 8, 5, 19)); g.draw(new Ellipse2D.Double(3, 17, 4, 4));
            }
            case "flag" -> { g.draw(new Line2D.Double(5, 21, 5, 4)); g.draw(path(5, 4, 18, 4, 15, 8.5, 18, 13, 5, 13)); }
            case "moon" -> g.draw(path(20, 14.5, 18.5, 15, 17, 15.2, 13, 14, 10, 11, 8.8, 7, 9, 5, 9.5, 3.5, 6, 5, 3.6, 8.5, 3.5, 12.5, 5, 16.5, 8, 19.5, 12, 20.5, 16, 19.5, 18.6, 17.4, 20, 14.5));
            case "sun" -> {
                g.draw(new Ellipse2D.Double(8, 8, 8, 8));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    g.draw(new Line2D.Double(12 + Math.cos(a) * 6.8, 12 + Math.sin(a) * 6.8, 12 + Math.cos(a) * 9.3, 12 + Math.sin(a) * 9.3));
                }
            }
            default -> g.draw(new Ellipse2D.Double(5, 5, 14, 14));
        }
    }

    private static Path2D path(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        return p;
    }
}
