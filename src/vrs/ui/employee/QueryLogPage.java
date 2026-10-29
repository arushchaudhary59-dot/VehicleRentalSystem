package vrs.ui.employee;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import vrs.model.Models.LogEntry;
import vrs.service.LogService;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Every SQL statement the app has run (from QUERY_LOG), with filters and a highlighted detail view. */
final class QueryLogPage extends JPanel implements EmployeeShell.Refreshable {
    private final JTextField search = Input.text("Search SQL text or user e-mail", "search");
    private final Map<String, Chip> typeChips = new LinkedHashMap<>();
    private final Chip errorsOnly = new Chip("Errors only").icon("alert").accent(Theme.DANGER);
    private final Chip live = new Chip("Live").icon("refresh");
    private final Tables.RowModel model = new Tables.RowModel(
            new String[]{"#", "Time", "User", "Type", "Statement", "Rows", "ms", "Result"},
            new Class<?>[]{Long.class, String.class, String.class, String.class, String.class, String.class, Integer.class, String.class});
    private final JTable table = Tables.create(model);
    private final JTextPane detail = new JTextPane();
    private final JLabel detailHead = Ui.label("Select a statement to see it in full", Theme.bold(13), Theme.TEXT_2);
    private final JLabel total = new JLabel("–"), totalNote = new JLabel(" ");
    private final JLabel avg = new JLabel("–"), avgNote = new JLabel(" ");
    private final JLabel errors = new JLabel("–"), errorsNote = new JLabel(" ");
    private final JLabel today = new JLabel("–"), todayNote = new JLabel(" ");
    private final javax.swing.Timer liveTimer = new javax.swing.Timer(3000, e -> refresh());
    private final javax.swing.Timer debounce = new javax.swing.Timer(300, e -> refresh());
    private List<LogEntry> rows = new ArrayList<>();

    QueryLogPage(EmployeeShell shell) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(EmployeeShell.pagePad());
        debounce.setRepeats(false);

        Btn clear = new Btn("Clear log", Btn.Kind.DANGER, "trash").small();
        clear.addActionListener(e -> {
            if (Modal.confirm(this, "Clear the query log?", "Deletes every row in QUERY_LOG. This cannot be undone.", "Clear log", true))
                Async.act(LogService::clear, () -> { Toast.info("Query log cleared."); refresh(); });
        });
        Btn reload = new Btn("", Btn.Kind.SECONDARY, "refresh").small().tip("Reload");
        reload.addActionListener(e -> refresh());

        JPanel top = Ui.transparent(null);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JComponent h = EmployeeShell.header("Query Log", "Every SQL statement sent through JDBC, with the values that were bound to it.", live, reload, clear);
        h.setAlignmentX(0f);
        top.add(h);
        top.add(Box.createVerticalStrut(16));
        JPanel stats = Ui.transparent(new GridLayout(1, 4, 16, 0));
        stats.add(Widgets.stat("terminal", Theme.INFO, "Statements logged", total, totalNote));
        stats.add(Widgets.stat("clock", Theme.ACCENT, "Average time", avg, avgNote));
        stats.add(Widgets.stat("alert", Theme.DANGER, "Errors", errors, errorsNote));
        stats.add(Widgets.stat("trend", Theme.VIOLET, "Today", today, todayNote));
        stats.setAlignmentX(0f);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        stats.setPreferredSize(new Dimension(10, 104));
        top.add(stats);
        top.add(Box.createVerticalStrut(16));

        JPanel filters = Ui.transparent(new BorderLayout(12, 0));
        search.setPreferredSize(new Dimension(320, 40));
        filters.add(search, BorderLayout.WEST);
        JPanel chips = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 8, 3));
        for (String t : new String[]{"ALL", "SELECT", "INSERT", "UPDATE", "DELETE", "TCL"}) {
            Chip c = new Chip(t.equals("TCL") ? "TRANSACTIONS" : t).accent(t.equals("ALL") ? Theme.ACCENT : Theme.statusColor(t));
            typeChips.put(t, c);
            chips.add(c);
            c.addActionListener(e -> { typeChips.forEach((k, v) -> v.setSelected(v == c)); refresh(); });
        }
        typeChips.get("ALL").setSelected(true);
        chips.add(errorsOnly);
        errorsOnly.addActionListener(e -> refresh());
        filters.add(chips, BorderLayout.CENTER);
        filters.setAlignmentX(0f);
        filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        top.add(filters);
        top.add(Box.createVerticalStrut(14));
        add(top, BorderLayout.NORTH);

        table.getColumnModel().getColumn(0).setCellRenderer(new Tables.Muted());
        table.getColumnModel().getColumn(1).setCellRenderer(new Tables.Mono());
        table.getColumnModel().getColumn(2).setCellRenderer(new Tables.TwoLine());
        table.getColumnModel().getColumn(3).setCellRenderer(new Tables.BadgeCell());
        table.getColumnModel().getColumn(4).setCellRenderer(new Tables.Mono() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, s, f, r, c);
                setForeground(Theme.TEXT);
                return this;
            }
        });
        table.getColumnModel().getColumn(5).setCellRenderer(new Tables.Muted());
        table.getColumnModel().getColumn(7).setCellRenderer(new Tables.BadgeCell());
        Tables.widths(table, 56, 110, 210, 110, 500, 64, 56, 100);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(null);

        detail.setEditable(false);
        detail.setBackground(Theme.SURFACE_2);
        detail.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        detail.setFont(Theme.mono(13));
        Card detailCard = new Card(new BorderLayout(0, 8)).pad(14, 16, 14, 16);
        JPanel dh = Ui.transparent(new BorderLayout());
        dh.add(detailHead, BorderLayout.WEST);
        Btn copy = new Btn("Copy SQL", Btn.Kind.GHOST, "clipboard").small();
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(detail.getText()), null);
            Toast.info("SQL copied to clipboard.");
        });
        dh.add(copy, BorderLayout.EAST);
        detailCard.add(dh, BorderLayout.NORTH);
        JScrollPane dsp = Scroll.of(detail);
        detailCard.add(dsp, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, Tables.inCard(table), detailCard);
        split.setResizeWeight(0.62);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        split.setDividerSize(12);
        split.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics g) {
                        g.setColor(Theme.BORDER_HI);
                        g.fillRoundRect(getWidth() / 2 - 20, getHeight() / 2 - 2, 40, 4, 4, 4);
                    }
                };
            }
        });
        add(split, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });
        live.addActionListener(e -> { if (live.isSelected()) liveTimer.start(); else liveTimer.stop(); });
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showDetail(); });
    }

    @Override public void refresh() {
        String type = typeChips.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).findFirst().orElse("ALL");
        boolean err = errorsOnly.isSelected();
        String q = search.getText();
        Async.run(() -> new Object[]{LogService.recent(type, err, q, 500), LogService.stats()}, data -> {
            @SuppressWarnings("unchecked") List<LogEntry> list = (List<LogEntry>) data[0];
            LogService.Stats s = (LogService.Stats) data[1];
            Long sel = table.getSelectedRow() >= 0 ? (Long) model.getValueAt(table.getSelectedRow(), 0) : null;
            rows = list;
            List<Object[]> r = new ArrayList<>();
            for (LogEntry e : list)
                r.add(new Object[]{e.id(), Fmt.time(e.at()).substring(0, 8), e.role() + "|" + (e.email() == null ? "not logged in" : e.email()),
                        e.type().equals("TCL") ? "TCL" : e.type(), e.sql(), e.rows() == null ? "–" : String.valueOf(e.rows()), e.ms(), e.status()});
            model.setRows(r);
            if (sel != null) for (int i = 0; i < model.getRowCount(); i++) if (sel.equals(model.getValueAt(i, 0))) { table.setRowSelectionInterval(i, i); break; }
            total.setText(String.format("%,d", s.total()));
            totalNote.setText("showing latest " + list.size());
            avg.setText(String.format("%.1f ms", s.avgMs()));
            avgNote.setText("per statement");
            errors.setText(String.valueOf(s.errors()));
            errorsNote.setText(s.errors() == 0 ? "No failed statements" : "Rejected statements");
            today.setText(String.valueOf(s.today()));
            todayNote.setText("statements since midnight");
        });
    }

    private void showDetail() {
        int r = table.getSelectedRow();
        if (r < 0 || r >= rows.size()) return;
        LogEntry e = rows.get(r);
        detailHead.setText("#" + e.id() + "   ·   " + e.type() + "   ·   " + Fmt.dateTime(e.at()) + "   ·   "
                + (e.ms() == null ? "" : e.ms() + " ms") + (e.rows() == null ? "" : "   ·   " + e.rows() + " row(s)"));
        highlight(pretty(e.sql()) + (e.error() == null ? "" : "\n\n-- ERROR: " + e.error()));
        detail.setCaretPosition(0);
    }

    private static final Pattern BREAK = Pattern.compile("\\s(FROM|WHERE|LEFT JOIN|JOIN|ORDER BY|GROUP BY|LIMIT|VALUES|SET|AND NOT EXISTS)\\s");
    private static final Pattern TOKENS = Pattern.compile("('(?:[^']|'')*')|(--[^\\n]*)|\\b(\\d+(?:\\.\\d+)?)\\b|\\b([A-Z_]{2,})\\b");
    private static final Set<String> KEYWORDS = Set.of("SELECT", "FROM", "WHERE", "JOIN", "LEFT", "ON", "AND", "OR", "INSERT", "INTO",
            "VALUES", "UPDATE", "SET", "DELETE", "ORDER", "BY", "GROUP", "LIMIT", "AS", "CASE", "WHEN", "THEN", "END", "NOT",
            "EXISTS", "IN", "IS", "NULL", "START", "TRANSACTION", "COMMIT", "ROLLBACK", "FOR", "DESC", "ASC", "LIKE", "DISTINCT");
    private static final Set<String> FUNCTIONS = Set.of("COUNT", "SUM", "AVG", "COALESCE", "CONCAT", "ROUND", "DATEDIFF", "CURDATE", "UPPER", "DATABASE");

    private static String pretty(String sql) {
        return BREAK.matcher(sql).replaceAll("\n$1 ");
    }

    private void highlight(String text) {
        StyledDocument doc = detail.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, text, style(Theme.TEXT, false));
            Matcher m = TOKENS.matcher(text);
            while (m.find()) {
                if (m.group(1) != null) doc.setCharacterAttributes(m.start(1), m.group(1).length(), style(Theme.readable(Theme.ACCENT), false), true);
                else if (m.group(2) != null) doc.setCharacterAttributes(m.start(2), m.group(2).length(), style(Theme.DANGER, false), true);
                else if (m.group(3) != null) doc.setCharacterAttributes(m.start(3), m.group(3).length(), style(Theme.WARN, false), true);
                else if (m.group(4) != null) {
                    String w = m.group(4);
                    if (KEYWORDS.contains(w)) doc.setCharacterAttributes(m.start(4), w.length(), style(Theme.readable(Theme.INFO), true), true);
                    else if (FUNCTIONS.contains(w)) doc.setCharacterAttributes(m.start(4), w.length(), style(Theme.VIOLET, false), true);
                }
            }
        } catch (BadLocationException ignored) { }
    }

    private static SimpleAttributeSet style(Color c, boolean bold) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, c);
        StyleConstants.setBold(a, bold);
        StyleConstants.setFontFamily(a, Theme.mono(13).getFamily());
        StyleConstants.setFontSize(a, 13);
        return a;
    }
}
