package vrs.ui.comp;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import vrs.ui.*;

/**
 * Dark rounded modal dialog: title, body, inline error line and buttons.
 * Dims the main window while open.
 */
public class Modal extends JDialog {
    protected final JPanel body = new JPanel();
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    private final JLabel error = new JLabel(" ");
    private Btn primary;

    public Modal(Component parent, String title, String subtitle, int width) {
        super(SwingUtilities.getWindowAncestor(parent), ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        boolean translucent = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        if (translucent) setBackground(new Color(0, 0, 0, 0));

        Card card = new Card(new BorderLayout(0, 14)).radius(translucent ? 20 : 0).fill(Theme.SURFACE).border(Theme.BORDER_HI);
        card.pad(22, 24, 20, 24);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Ui.label(title, Theme.bold(19), Theme.TEXT));
        if (subtitle != null) {
            titles.add(Box.createVerticalStrut(4));
            titles.add(Ui.label(subtitle, Theme.font(13), Theme.TEXT_2));
        }
        head.add(titles, BorderLayout.CENTER);
        Btn close = new Btn("", Btn.Kind.GHOST, "x").small();
        close.setFocusable(false);
        close.addActionListener(e -> dispose());
        JPanel cw = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        cw.setOpaque(false);
        cw.add(close);
        head.add(cw, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        card.add(body, BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout(0, 10));
        foot.setOpaque(false);
        error.setFont(Theme.font(12.5f));
        error.setForeground(Theme.DANGER);
        foot.add(error, BorderLayout.NORTH);
        buttons.setOpaque(false);
        foot.add(buttons, BorderLayout.EAST);
        card.add(foot, BorderLayout.SOUTH);

        setContentPane(card);
        card.setPreferredSize(null);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        this.width = width;
    }

    private final int width;

    public Btn addButton(String text, Btn.Kind kind, Runnable action) {
        Btn b = new Btn(text, kind);
        b.addActionListener(e -> action.run());
        buttons.add(b);
        if (kind == Btn.Kind.PRIMARY || kind == Btn.Kind.DANGER) { primary = b; getRootPane().setDefaultButton(b); }
        return b;
    }

    public Btn addCancel(String text) { return addButton(text, Btn.Kind.SECONDARY, this::dispose); }

    public void showError(String msg) {
        error.setText(msg == null || msg.isBlank() ? " " : "<html><body style='width:" + (width - 80) + "px'>" + msg + "</body></html>");
        pack();
    }

    /** Disables the buttons while a database call is running. */
    public void busy(boolean on, String label) {
        for (Component c : buttons.getComponents()) c.setEnabled(!on);
        if (primary != null && label != null) primary.setText(label);
        setCursor(Cursor.getPredefinedCursor(on ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    public void open() {
        Component glass = null;
        Window owner = getOwner();
        JRootPane root = owner instanceof RootPaneContainer rpc ? rpc.getRootPane() : null;
        if (root != null) {
            glass = root.getGlassPane();
            JComponent dim = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    g.setColor(new Color(4, 8, 12, 150));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            dim.addMouseListener(new MouseAdapter() { });
            root.setGlassPane(dim);
            dim.setVisible(true);
        }
        pack();
        Dimension d = getSize();
        if (d.width < width) setSize(width, d.height);
        setLocationRelativeTo(owner);
        setVisible(true);                                         // blocks until closed
        if (root != null) { root.getGlassPane().setVisible(false); root.setGlassPane(glass); }
    }

    // ------------------------------------------------------------------ ready-made dialogs
    public static boolean confirm(Component parent, String title, String message, String okText, boolean danger) {
        boolean[] ok = {false};
        Modal m = new Modal(parent, title, null, 440);
        JLabel l = Ui.label("<html><body style='width:360px'>" + message + "</body></html>", Theme.font(14), Theme.TEXT_2);
        m.body.add(Ui.left(l));
        m.addCancel("Keep");
        m.addButton(okText, danger ? Btn.Kind.DANGER : Btn.Kind.PRIMARY, () -> { ok[0] = true; m.dispose(); });
        m.open();
        return ok[0];
    }

    public static void info(Component parent, String title, String message) {
        Modal m = new Modal(parent, title, null, 440);
        m.body.add(Ui.left(Ui.label("<html><body style='width:360px'>" + message + "</body></html>", Theme.font(14), Theme.TEXT_2)));
        m.addButton("OK", Btn.Kind.PRIMARY, m::dispose);
        m.open();
    }

    /** Adds "Label" above a field, with spacing, to the body. */
    public void field(String label, JComponent field) {
        body.add(Ui.left(Ui.label(label, Theme.bold(12), Theme.TEXT_2)));
        body.add(Box.createVerticalStrut(6));
        field.setAlignmentX(0f);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
        body.add(field);
        body.add(Box.createVerticalStrut(14));
    }

    public void row(JComponent... comps) {
        JPanel p = new JPanel(new GridLayout(1, comps.length, 12, 0));
        p.setOpaque(false);
        for (JComponent c : comps) p.add(c);
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        body.add(p);
        body.add(Box.createVerticalStrut(14));
    }

    /** A label + field column for use with row(...). */
    public static JPanel labelled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(Ui.label(label, Theme.bold(12), Theme.TEXT_2), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    public JPanel body() { return body; }
}
