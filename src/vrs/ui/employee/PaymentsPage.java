package vrs.ui.employee;

import java.awt.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import vrs.db.DbErrors;
import vrs.model.Models.*;
import vrs.service.*;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Payment history with filters, totals, and recording a desk payment. */
final class PaymentsPage extends JPanel implements EmployeeShell.Refreshable {
    private final EmployeeShell shell;
    private final JTextField search = Input.text("Search name, booking # or reference", "search");
    private final Map<String, Chip> statusChips = new LinkedHashMap<>();
    private final Select<String> method = new Select<>(List.of("All methods", "UPI", "Card", "Net Banking", "Cash"));
    private final Tables.RowModel model = new Tables.RowModel(
            new String[]{"#", "Date", "Booking", "Vehicle", "Method", "Status", "Amount", "Reference"},
            new Class<?>[]{Integer.class, String.class, String.class, String.class, String.class, String.class, BigDecimal.class, String.class});
    private final JTable table = Tables.create(model);
    private final JLabel collected = new JLabel("–"), collectedNote = new JLabel(" ");
    private final JLabel refunded = new JLabel("–"), refundedNote = new JLabel(" ");
    private final JLabel pendingAmt = new JLabel("–"), pendingNote = new JLabel(" ");
    private final JLabel failed = new JLabel("–"), failedNote = new JLabel(" ");
    private final JLabel count = Ui.label(" ", Theme.font(12.5f), Theme.TEXT_3);
    private List<Payment> all = new ArrayList<>();

    PaymentsPage(EmployeeShell shell) {
        super(new BorderLayout());
        this.shell = shell;
        setOpaque(false);
        setBorder(EmployeeShell.pagePad());

        Btn record = new Btn("Record payment", Btn.Kind.PRIMARY, "plus");
        record.addActionListener(e -> { if (recordDialog()) refresh(); });
        Btn reload = new Btn("", Btn.Kind.SECONDARY, "refresh").tip("Reload");
        reload.addActionListener(e -> refresh());
        JPanel top = Ui.transparent(null);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JComponent h = EmployeeShell.header("Payments", "Every payment and refund recorded in the PAYMENT table.", reload, record);
        h.setAlignmentX(0f);
        top.add(h);
        top.add(Box.createVerticalStrut(18));

        JPanel stats = Ui.transparent(new GridLayout(1, 4, 16, 0));
        stats.add(Widgets.stat("wallet", Theme.ACCENT, "Collected (all time)", collected, collectedNote));
        stats.add(Widgets.stat("refresh", Theme.VIOLET, "Refunded", refunded, refundedNote));
        stats.add(Widgets.stat("clock", Theme.WARN, "Pending", pendingAmt, pendingNote));
        stats.add(Widgets.stat("alert", Theme.DANGER, "Failed attempts", failed, failedNote));
        stats.setAlignmentX(0f);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        stats.setPreferredSize(new Dimension(10, 104));
        top.add(stats);
        top.add(Box.createVerticalStrut(16));

        JPanel filters = Ui.transparent(new BorderLayout(12, 0));
        search.setPreferredSize(new Dimension(340, 40));
        filters.add(search, BorderLayout.WEST);
        JPanel chips = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 8, 3));
        for (String s : new String[]{"All", "Success", "Refunded", "Pending", "Failed"}) {
            Chip c = new Chip(s).accent(s.equals("All") ? Theme.ACCENT : Theme.statusColor(s));
            statusChips.put(s, c);
            chips.add(c);
            c.addActionListener(e -> { statusChips.forEach((k, v) -> v.setSelected(v == c)); apply(); });
        }
        statusChips.get("All").setSelected(true);
        filters.add(chips, BorderLayout.CENTER);
        method.setPreferredSize(new Dimension(170, 40));
        filters.add(method, BorderLayout.EAST);
        filters.setAlignmentX(0f);
        filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        top.add(filters);
        top.add(Box.createVerticalStrut(14));
        add(top, BorderLayout.NORTH);

        table.getColumnModel().getColumn(1).setCellRenderer(new Tables.TwoLine());
        table.getColumnModel().getColumn(2).setCellRenderer(new Tables.TwoLine());
        table.getColumnModel().getColumn(3).setCellRenderer(new Tables.VehicleCell());
        table.getColumnModel().getColumn(5).setCellRenderer(new Tables.BadgeCell());
        table.getColumnModel().getColumn(7).setCellRenderer(new Tables.Mono());
        table.setRowHeight(52);
        Tables.widths(table, 56, 120, 190, 220, 110, 120, 110, 190);
        add(Tables.inCard(table), BorderLayout.CENTER);
        count.setBorder(BorderFactory.createEmptyBorder(12, 2, 0, 0));
        add(count, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { apply(); }
            public void removeUpdate(DocumentEvent e) { apply(); }
            public void changedUpdate(DocumentEvent e) { apply(); }
        });
        method.addActionListener(e -> apply());
    }

    @Override public void refresh() {
        Async.run(PaymentService::all, list -> { all = list; totals(); apply(); });
    }

    private void totals() {
        BigDecimal ok = BigDecimal.ZERO, ref = BigDecimal.ZERO, pend = BigDecimal.ZERO, month = BigDecimal.ZERO;
        int nOk = 0, nRef = 0, nPend = 0, nFail = 0;
        YearMonth now = YearMonth.now();
        for (Payment p : all) {
            switch (p.status()) {
                case "Success" -> { ok = ok.add(p.amount()); nOk++; if (YearMonth.from(p.date()).equals(now)) month = month.add(p.amount()); }
                case "Refunded" -> { ref = ref.add(p.amount()); nRef++; }
                case "Pending" -> { pend = pend.add(p.amount()); nPend++; }
                case "Failed" -> nFail++;
                default -> { }
            }
        }
        collected.setText(Fmt.money(ok));
        collectedNote.setText(Fmt.money(month) + " this month");
        refunded.setText(Fmt.money(ref));
        refundedNote.setText(nRef + " refunds");
        pendingAmt.setText(Fmt.money(pend));
        pendingNote.setText(nPend + " awaiting the gateway");
        failed.setText(String.valueOf(nFail));
        failedNote.setText("Declined transactions");
    }

    private void apply() {
        String status = statusChips.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).findFirst().orElse("All");
        String m = method.getSelectedIndex() <= 0 ? null : method.value();
        String q = search.getText().trim().toLowerCase();
        List<Object[]> rows = new ArrayList<>();
        for (Payment p : all) {
            if (!status.equals("All") && !p.status().equals(status)) continue;
            if (m != null && !p.method().equals(m)) continue;
            if (!q.isEmpty() && !("#" + p.bookingId() + " " + p.customerName() + " " + p.vehicleName() + " " + p.txnRef()).toLowerCase().contains(q)) continue;
            rows.add(new Object[]{p.id(), Fmt.date(p.date().toLocalDate()) + "|" + Fmt.shortTime(p.date()),
                    "Booking #" + p.bookingId() + "|" + p.customerName(),
                    p.bodyType() + "|" + p.vehicleName() + "|", p.method(), p.status(), p.amount(), p.txnRef() == null ? "—" : p.txnRef()});
        }
        model.setRows(rows);
        count.setText(rows.size() + " of " + all.size() + " payments");
    }

    /** Record a payment taken at the desk for a confirmed / completed booking with a balance. */
    private boolean recordDialog() {
        Modal m = new Modal(this, "Record payment", "Cash or card-machine payment taken at the desk.", 520);
        record BItem(Booking b) {
            @Override public String toString() { return "#" + b.id() + "  ·  " + b.customerName() + "  ·  " + b.vehicleName() + "  ·  due " + Fmt.money(b.balance()); }
        }
        Select<BItem> booking = new Select<>(List.of());
        Select<String> meth = new Select<>(PaymentService.ALL_METHODS);
        JTextField amount = Input.text("Amount", "wallet");
        m.field("Booking with a balance", booking);
        m.row(Modal.labelled("Method", meth), Modal.labelled("Amount (" + Theme.RUPEE.trim() + ")", amount));
        booking.addActionListener(e -> { BItem b = booking.value(); if (b != null) amount.setText(b.b().balance().toPlainString()); });
        Async.run(BookingService::all, list -> {
            List<BItem> items = new ArrayList<>();
            for (Booking b : list)
                if ((b.status().equals("Confirmed") || b.status().equals("Completed")) && b.balance().signum() > 0) items.add(new BItem(b));
            booking.setItems(items);
            if (items.isEmpty()) m.showError("No confirmed booking has an outstanding balance.");
            else { booking.setSelectedIndex(0); }
        });
        boolean[] ok = {false};
        m.addCancel("Cancel");
        m.addButton("Record", Btn.Kind.PRIMARY, () -> {
            BItem b = booking.value();
            if (b == null) { m.showError("Choose a booking."); return; }
            BigDecimal amt;
            try { amt = new BigDecimal(amount.getText().trim()); } catch (NumberFormatException ex) { m.showError("Amount must be a number."); return; }
            String mt = meth.value();
            m.busy(true, "Saving…");
            Async.run(() -> PaymentService.record(b.b().id(), amt, mt), ref -> {
                ok[0] = true;
                m.dispose();
                Toast.success(Fmt.money(amt) + " recorded for booking #" + b.b().id() + (ref.isEmpty() ? "" : "  ·  " + ref));
            }, e -> { m.busy(false, "Record"); m.showError(DbErrors.friendly(e)); });
        });
        m.open();
        return ok[0];
    }
}
