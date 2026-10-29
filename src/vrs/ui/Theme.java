package vrs.ui;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.metal.MetalLookAndFeel;

/** Colours, fonts and the global Swing setup for the dark and light themes. */
public final class Theme {
    private Theme() {}

    // ------------------------------------------------------------------ palette
    // Not final: they are swapped when the user switches between dark and light mode.
    public static Color BG, SURFACE, SURFACE_2, SURFACE_3, BORDER, BORDER_HI, TEXT, TEXT_2, TEXT_3;
    public static Color ACCENT, ACCENT_HI, ACCENT_INK, INFO, WARN, DANGER, VIOLET;

    private static boolean light;
    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userRoot().node("vrs-vehicle-rental");

    static { setLight(PREFS.getBoolean("lightMode", false)); }

    public static boolean isLight() { return light; }

    /** Switches the palette. Screens must be rebuilt afterwards (App.toggleTheme does that). */
    public static void setLight(boolean on) {
        light = on;
        try { PREFS.putBoolean("lightMode", on); } catch (Exception ignored) { }
        if (on) {
            BG = new Color(0xF3F5F8); SURFACE = new Color(0xFFFFFF); SURFACE_2 = new Color(0xF0F3F7); SURFACE_3 = new Color(0xE3E9F0);
            BORDER = new Color(0xDCE2EA); BORDER_HI = new Color(0xC2CCD8);
            TEXT = new Color(0x111821); TEXT_2 = new Color(0x4A5768); TEXT_3 = new Color(0x7B8899);
            ACCENT = new Color(0x0FA968); ACCENT_HI = new Color(0x0B8B56); ACCENT_INK = new Color(0xFFFFFF);
            INFO = new Color(0x2F7FE0); WARN = new Color(0xC4870F); DANGER = new Color(0xD9434F); VIOLET = new Color(0x7656EC);
        } else {
            BG = new Color(0x0B0F14); SURFACE = new Color(0x121821); SURFACE_2 = new Color(0x18202B); SURFACE_3 = new Color(0x202A37);
            BORDER = new Color(0x253040); BORDER_HI = new Color(0x34465C);
            TEXT = new Color(0xE8EEF5); TEXT_2 = new Color(0xA3B1C2); TEXT_3 = new Color(0x6B7A8C);
            ACCENT = new Color(0x19C37D); ACCENT_HI = new Color(0x2FDB93); ACCENT_INK = new Color(0x04130C);
            INFO = new Color(0x4C9AFF); WARN = new Color(0xF5B547); DANGER = new Color(0xF0616D); VIOLET = new Color(0x9B7BFF);
        }
    }

    /** A coloured text that stays readable on the current background (lighter in dark mode, darker in light mode). */
    public static Color readable(Color c) { return light ? mix(c, Color.BLACK, 0.25) : mix(c, Color.WHITE, 0.25); }

    /** "#RRGGBB" for use inside HTML labels. */
    public static String hex(Color c) { return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()); }

    /** One colour per vehicle body type (chart bars, symbols). */
    public static final Map<String, Color> TYPE_COLORS = new LinkedHashMap<>();
    static {
        TYPE_COLORS.put("Hatchback", new Color(0x4C9AFF));
        TYPE_COLORS.put("Sedan", new Color(0x19C37D));
        TYPE_COLORS.put("SUV", new Color(0xF5B547));
        TYPE_COLORS.put("MUV", new Color(0xFF8A4C));
        TYPE_COLORS.put("Cruiser", new Color(0xE0679B));
        TYPE_COLORS.put("Sports", new Color(0xF0616D));
        TYPE_COLORS.put("Commuter", new Color(0x9B7BFF));
        TYPE_COLORS.put("Scooter", new Color(0x2CC6D6));
    }

    public static Color typeColor(String body) { return TYPE_COLORS.getOrDefault(body, ACCENT); }

    public static Color statusColor(String status) {
        if (status == null) return TEXT_3;
        return switch (status) {
            case "Pending", "Under Maintenance" -> WARN;
            case "Confirmed", "Success", "Available", "OK" -> ACCENT;
            case "Completed", "SELECT" -> INFO;
            case "Cancelled", "Failed", "Retired", "ERROR", "DELETE" -> DANGER;
            case "Refunded", "UPDATE", "Manager" -> VIOLET;
            case "Rental Operator" -> INFO;
            case "INSERT" -> ACCENT;
            case "TCL" -> new Color(0x2CC6D6);
            default -> TEXT_3;
        };
    }

    public static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    // ------------------------------------------------------------------ fonts
    private static final String SANS = pick(List.of("Inter", "SF Pro Text", "Helvetica Neue", "Segoe UI", "Roboto", "Arial"), "SansSerif");
    private static final String MONO = pick(List.of("JetBrains Mono", "SF Mono", "Menlo", "Consolas", "DejaVu Sans Mono"), "Monospaced");

    public static Font font(float size) { return new Font(SANS, Font.PLAIN, 1).deriveFont(size); }
    public static Font bold(float size) { return new Font(SANS, Font.BOLD, 1).deriveFont(size); }
    public static Font mono(float size) { return new Font(MONO, Font.PLAIN, 1).deriveFont(size); }

    /** "₹" when the font can draw it, otherwise "Rs ". */
    public static final String RUPEE = font(13).canDisplay('₹') ? "₹" : "Rs ";

    /** First installed font from the list, preferring one that can draw the rupee sign. */
    private static String pick(List<String> wanted, String fallback) {
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String w : wanted) if (have.contains(w) && new Font(w, Font.PLAIN, 13).canDisplay('\u20B9')) return w;
        for (String w : wanted) if (have.contains(w)) return w;
        return fallback;
    }

    // ------------------------------------------------------------------ Swing defaults
    public static void install() {
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException ignored) { }

        FontUIResource f = new FontUIResource(font(13));
        for (Object key : Collections.list(UIManager.getDefaults().keys()))
            if (key.toString().endsWith(".font")) UIManager.put(key, f);

        ColorUIResource bg = new ColorUIResource(BG), surface = new ColorUIResource(SURFACE),
                s2 = new ColorUIResource(SURFACE_2), text = new ColorUIResource(TEXT),
                sel = new ColorUIResource(alpha(ACCENT, 70)), border = new ColorUIResource(BORDER);
        Object[] d = {
                "Panel.background", bg, "Label.foreground", text, "Label.background", bg,
                "OptionPane.background", surface, "OptionPane.messageForeground", text,
                "Viewport.background", bg, "ScrollPane.background", bg,
                "TextField.background", s2, "TextField.foreground", text, "TextField.caretForeground", text,
                "TextField.selectionBackground", sel, "TextField.selectionForeground", text,
                "TextField.inactiveForeground", new ColorUIResource(TEXT_3),
                "PasswordField.background", s2, "PasswordField.foreground", text, "PasswordField.caretForeground", text,
                "PasswordField.selectionBackground", sel,
                "TextArea.background", s2, "TextArea.foreground", text, "TextArea.caretForeground", text,
                "TextArea.selectionBackground", sel,
                "TextPane.background", s2, "TextPane.foreground", text, "TextPane.selectionBackground", sel,
                "ComboBox.background", s2, "ComboBox.foreground", text, "ComboBox.selectionBackground", sel,
                "ComboBox.selectionForeground", text, "ComboBox.buttonBackground", s2,
                "ComboBox.disabledBackground", s2, "ComboBox.disabledForeground", new ColorUIResource(TEXT_3),
                "List.background", s2, "List.foreground", text, "List.selectionBackground", sel, "List.selectionForeground", text,
                "Table.background", surface, "Table.foreground", text, "Table.selectionBackground", new ColorUIResource(SURFACE_3),
                "Table.selectionForeground", text, "Table.gridColor", border, "Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder(),
                "TableHeader.background", surface, "TableHeader.foreground", new ColorUIResource(TEXT_2),
                "PopupMenu.background", s2, "PopupMenu.border", BorderFactory.createLineBorder(BORDER_HI),
                "MenuItem.background", s2, "MenuItem.foreground", text, "MenuItem.selectionBackground", new ColorUIResource(SURFACE_3),
                "MenuItem.selectionForeground", text,
                "ToolTip.background", new ColorUIResource(SURFACE_3), "ToolTip.foreground", text,
                "ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_HI), BorderFactory.createEmptyBorder(4, 8, 4, 8)),
                "CheckBox.background", bg, "CheckBox.foreground", text,
                "Separator.foreground", border, "Separator.background", bg,
                "SplitPane.background", bg, "SplitPane.dividerSize", 6, "SplitPaneDivider.border", BorderFactory.createEmptyBorder(),
                "SplitPane.border", BorderFactory.createEmptyBorder(),
                "Spinner.background", s2, "FormattedTextField.background", s2, "FormattedTextField.foreground", text,
                "Button.background", s2, "Button.foreground", text, "Button.select", new ColorUIResource(SURFACE_3),
                "Button.focus", new ColorUIResource(alpha(ACCENT, 0)),
                "FileChooser.background", surface,
        };
        for (int i = 0; i < d.length; i += 2) UIManager.put(d[i], d[i + 1]);
        ToolTipManager.sharedInstance().setInitialDelay(250);
    }

    /** Anti-aliased graphics (always use this in paintComponent). */
    public static Graphics2D g2(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g2;
    }
}
