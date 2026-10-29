package vrs.ui.employee;

import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import vrs.model.Models.Booking;
import vrs.service.BookingService;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Full CRUD on bookings: create, read (filters), update, confirm / complete / cancel, delete. */
final class BookingsPage extends JPanel implements EmployeeShell.Refreshable {
    private final EmployeeShell shell;
    private final JTextField search = Input.text("Search customer, vehicle, reg. no or #id", "search");
    private final Map<String, Chip> statusChips = new LinkedHashMap<>();
    private final Seg when = new Seg("All dates", "Upcoming", "Past");
    private final Tables.RowModel model = new Tables.RowModel(
            new String[]{"#", "Customer", "Vehicle", "Dates", "Status", "Total", "Paid", "Handled by"},
            new Class<?>[]{Integer.class, String.class, String.class, String.class, String.class, BigDecimal.class, BigDecimal.class, String.class});
    private final JTable table = Tables.create(model);
    private final JLabel count = Ui.label(" ", Theme.font(12.5f), Theme.TEXT_3);
    private final Btn confirmBtn = new Btn("Confirm", Btn.Kind.SUBTLE, "check").small();
    private final Btn completeBtn = new Btn("Complete", Btn.Kind.SECONDARY, "flag").small();
    private final Btn editBtn = new Btn("Edit", Btn.Kind.SECONDARY, "edit").small();
    private final Btn cancelBtn = new Btn("Cancel", Btn.Kind.SECONDARY, "x").small();
    private final Btn deleteBtn = new Btn("Delete", Btn.Kind.DANGER, "trash").small();
    private List<Booking> all = new ArrayList<>();
    private List<Booking> shown = new ArrayList<>();

    BookingsPage(EmployeeShell shell) {
        super(new BorderLayout(0, 0));
        this.shell = shell;
        setOpaque(false);
        setBorder(EmployeeShell.pagePad());

        Btn add = new Btn("New booking", Btn.Kind.PRIMARY, "plus");
        add.addActionListener(e -> { if (BookingFormDialog.open(this, shell.user, null)) refresh(); });
        Btn reload = new Btn("", Btn.Kind.SECONDARY, "refresh").tip("Reload");
        reload.addActionListener(e -> refresh());

        JPanel top = Ui.transparent(null);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JComponent h = EmployeeShell.header("Bookings", "Confirm requests, edit, complete, cancel or delete bookings.", when, reload, add);
        h.setAlignmentX(0f);
        top.add(h);
        top.add(Box.createVerticalStrut(18));

        JPanel filters = Ui.transparent(new BorderLayout(12, 0));
        search.setPreferredSize(new Dimension(320, 40));
        filters.add(search, BorderLayout.WEST);
        JPanel chips = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 8, 3));
        for (String s : new String[]{"All", "Pending", "Confirmed", "Completed", "Cancelled"}) {
            Chip c = new Chip(s).accent(s.equals("All") ? Theme.ACCENT : Theme.statusColor(s));
            statusChips.put(s, c);
            chips.add(c);
            c.addActionListener(e -> { statusChips.forEach((k, v) -> v.setSelected(v == c)); apply(); });
        }
        statusChips.get("All").setSelected(true);
        filters.add(chips, BorderLayout.CENTER);
        filters.setAlignmentX(0f);
        filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        top.add(filters);
        top.add(Box.createVerticalStrut(14));
        add(top, BorderLayout.NORTH);

        table.getColumnModel().getColumn(1).setCellRenderer(new Tables.TwoLine());
        table.getColumnModel().getColumn(2).setCellRenderer(new Tables.VehicleCell());
        table.getColumnModel().getColumn(3).setCellRenderer(new Tables.TwoLine());
        table.getColumnModel().getColumn(4).setCellRenderer(new Tables.BadgeCell());
        table.getColumnModel().getColumn(7).setCellRenderer(new Tables.Muted());
        table.setRowHeight(52);
        Tables.widths(table, 52, 210, 230, 200, 130, 100, 100, 110);
        add(Tables.inCard(table), BorderLayout.CENTER);

        JPanel bar = Ui.transparent(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(12, 2, 0, 0));
        bar.add(count, BorderLayout.WEST);
        JPanel actions = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        for (Btn b : new Btn[]{confirmBtn, completeBtn, editBtn, cancelBtn, deleteBtn}) actions.add(b);
        bar.add(actions, BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { apply(); }
            public void removeUpdate(DocumentEvent e) { apply(); }
            public void changedUpdate(DocumentEvent e) { apply(); }
        });
        when.onChange(w -> apply());
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) edit(); }
        });
        confirmBtn.addActionListener(e -> run("confirm"));
        completeBtn.addActionListener(e -> run("complete"));
        cancelBtn.addActionListener(e -> run("cancel"));
        deleteBtn.addActionListener(e -> run("delete"));
        editBtn.addActionListener(e -> edit());
        updateButtons();
    }

    void preset(String status) {
        statusChips.forEach((k, v) -> v.setSelected(k.equals(status)));
        when.select(0, false);
        search.setText("");
    }

    @Override public void refresh() {
        Async.run(BookingService::all, list -> {
            all = list;
            int p = 0;
            for (Booking b : list) if (b.status().equals("Pending")) p++;
            shell.setPendingCount(p);
            apply();
        });
    }

    private void apply() {
        String status = statusChips.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).findFirst().orElse("All");
        String q = search.getText().trim().toLowerCase();
        LocalDate today = LocalDate.now();
        Integer selectedId = selected() == null ? null : selected().id();
        shown = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (Booking b : all) {
            if (!status.equals("All") && !b.status().equals(status)) continue;
            if (when.index() == 1 && !b.end().isAfter(today)) continue;
            if (when.index() == 2 && b.end().isAfter(today)) continue;
            if (!q.isEmpty()) {
                String hay = ("#" + b.id() + " " + b.customerName() + " " + b.customerEmail() + " " + b.vehicleName() + " " + b.reg()).toLowerCase();
                if (!hay.contains(q)) continue;
            }
            shown.add(b);
            rows.add(new Object[]{b.id(), b.customerName() + "|" + b.customerEmail(),
                    b.bodyType() + "|" + b.vehicleName() + "|" + b.reg(),
                    Fmt.dm(b.start()) + "  →  " + Fmt.dm(b.end()) + "|" + b.start().getYear() + "  ·  " + Fmt.days(b.days()),
                    b.status(), b.total(), b.paid().subtract(b.refunded()),
                    b.handledByName() == null ? "—" : b.handledByName()});
        }
        model.setRows(rows);
        count.setText(shown.size() + " of " + all.size() + " bookings  ·  double-click a row to edit");
        if (selectedId != null) for (int i = 0; i < table.getRowCount(); i++)
            if (selectedId.equals(table.getValueAt(i, 0))) { table.setRowSelectionInterval(i, i); break; }
        updateButtons();
    }

    private Booking selected() {
        int v = table.getSelectedRow();
        if (v < 0) return null;
        int id = (Integer) model.getValueAt(table.convertRowIndexToModel(v), 0);
        for (Booking b : all) if (b.id() == id) return b;
        return null;
    }

    private void updateButtons() {
        Booking b = selected();
        String s = b == null ? "" : b.status();
        confirmBtn.setEnabled(s.equals("Pending"));
        completeBtn.setEnabled(s.equals("Confirmed"));
        cancelBtn.setEnabled(s.equals("Pending") || s.equals("Confirmed"));
        editBtn.setEnabled(b != null);
        deleteBtn.setEnabled(b != null);
    }

    private void edit() {
        Booking b = selected();
        if (b != null && BookingFormDialog.open(this, shell.user, b)) refresh();
    }

    private void run(String action) {
        Booking b = selected();
        if (b == null) return;
        int emp = shell.user.id();
        switch (action) {
            case "confirm" -> Async.act(() -> BookingService.confirm(b.id(), emp), () -> done("Booking #" + b.id() + " confirmed. The customer can now pay."));
            case "complete" -> {
                String warn = b.fullyPaid() ? "" : "<br><br><font color='" + Theme.hex(Theme.WARN) + "'>Note: " + Fmt.money(b.balance()) + " is still unpaid.</font>";
                if (Modal.confirm(this, "Mark #" + b.id() + " as completed?", "Use this when the vehicle has been returned." + warn, "Mark completed", false))
                    Async.act(() -> BookingService.complete(b.id(), emp), () -> done("Booking #" + b.id() + " completed."));
            }
            case "cancel" -> {
                BigDecimal net = b.paid().subtract(b.refunded());
                String msg = b.customerName() + " · " + b.vehicleName() + " · " + Fmt.day(b.start()) + " → " + Fmt.day(b.end())
                        + (net.signum() > 0 ? "<br><br>A refund of <b>" + Fmt.money(net) + "</b> will be recorded." : "");
                if (Modal.confirm(this, "Cancel booking #" + b.id() + "?", msg, "Cancel booking", true))
                    Async.act(() -> BookingService.cancel(b.id(), emp), () -> done("Booking #" + b.id() + " cancelled."));
            }
            case "delete" -> {
                if (Modal.confirm(this, "Delete booking #" + b.id() + " permanently?",
                        "This removes the row from the BOOKING table. Bookings that have payments cannot be deleted (their history must be kept), cancel those instead.",
                        "Delete", true))
                    Async.act(() -> BookingService.delete(b.id()), () -> done("Booking #" + b.id() + " deleted."));
            }
            default -> { }
        }
    }

    private void done(String msg) {
        Toast.success(msg);
        refresh();
    }
}
