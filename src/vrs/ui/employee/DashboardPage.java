package vrs.ui.employee;

import java.awt.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import javax.swing.*;
import vrs.model.Models.Booking;
import vrs.service.*;
import vrs.service.StatsService.*;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Overview: key numbers, sales by vehicle type for a month, and pending requests to act on. */
final class DashboardPage extends JPanel implements EmployeeShell.Refreshable {
    private final EmployeeShell shell;
    private final JLabel pending = new JLabel("–"), pendingNote = new JLabel(" ");
    private final JLabel onRoad = new JLabel("–"), onRoadNote = new JLabel(" ");
    private final JLabel collected = new JLabel("–"), collectedNote = new JLabel(" ");
    private final JLabel fleet = new JLabel("–"), fleetNote = new JLabel(" ");
    private final BarChart chart = new BarChart();
    private final JLabel monthLabel = Ui.label("", Theme.bold(13.5f), Theme.TEXT);
    private final JLabel chartInsight = Ui.label(" ", Theme.font(13), Theme.TEXT_2);
    private final Seg metric = new Seg("Revenue", "Rentals");
    private final JPanel requests = Ui.transparent(null);
    private YearMonth month = YearMonth.now();
    private List<TypeStat> stats = List.of();

    DashboardPage(EmployeeShell shell) {
        super(new BorderLayout());
        this.shell = shell;
        setOpaque(false);
        Page page = new Page();
        page.setBorder(EmployeeShell.pagePad());

        Btn refresh = new Btn("Refresh", Btn.Kind.SECONDARY, "refresh").small();
        refresh.addActionListener(e -> refresh());
        int hour = LocalTime.now().getHour();
        String hello = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        page.add(EmployeeShell.header(hello + ", " + shell.user.firstName(),
                Fmt.date(LocalDate.now()) + "  ·  here is what needs your attention", refresh), 22);

        JPanel statRow = Ui.transparent(new GridLayout(1, 4, 16, 0));
        Card c1 = Widgets.stat("clipboard", Theme.WARN, "Pending requests", pending, pendingNote);
        c1.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) { shell.showBookings("Pending"); }
        });
        statRow.add(c1);
        statRow.add(Widgets.stat("road", Theme.INFO, "On the road today", onRoad, onRoadNote));
        statRow.add(Widgets.stat("wallet", Theme.ACCENT, "Collected this month", collected, collectedNote));
        statRow.add(Widgets.stat("car", Theme.VIOLET, "Fleet available", fleet, fleetNote));
        statRow.setPreferredSize(new Dimension(10, 104));
        statRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        page.add(statRow, 18);

        JPanel main = Ui.transparent(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weighty = 1;
        g.weightx = 0.66;
        g.insets = new Insets(0, 0, 0, 16);
        main.add(chartCard(), g);
        g.weightx = 0.34;
        g.insets = new Insets(0, 0, 0, 0);
        main.add(requestsCard(), g);
        main.setPreferredSize(new Dimension(10, 470));
        main.setMaximumSize(new Dimension(Integer.MAX_VALUE, 470));
        page.add(main, 0);
        add(Scroll.of(page));

        metric.onChange(m -> { chart.setRevenue(m.equals("Revenue")); updateInsight(); });
    }

    private JComponent chartCard() {
        Card c = new Card(new BorderLayout(0, 12)).pad(20, 20, 16, 20);
        JPanel head = Ui.transparent(new BorderLayout(0, 12));
        head.add(Ui.col(4, Ui.label("Which vehicle type sells most", Theme.bold(16), Theme.TEXT), chartInsight), BorderLayout.NORTH);
        JPanel ctl = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ctl.setBorder(BorderFactory.createEmptyBorder(0, -8, 0, 0));
        Btn prev = new Btn("", Btn.Kind.SECONDARY, "chevLeft").small().tip("Previous month");
        Btn next = new Btn("", Btn.Kind.SECONDARY, "chevRight").small().tip("Next month");
        prev.addActionListener(e -> { month = month.minusMonths(1); loadChart(); });
        next.addActionListener(e -> { month = month.plusMonths(1); loadChart(); });
        monthLabel.setPreferredSize(new Dimension(130, 32));
        monthLabel.setHorizontalAlignment(SwingConstants.CENTER);
        ctl.add(prev); ctl.add(monthLabel); ctl.add(next);
        head.add(ctl, BorderLayout.WEST);
        JPanel mw = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        mw.add(metric);
        head.add(mw, BorderLayout.EAST);
        c.add(head, BorderLayout.NORTH);
        c.add(chart, BorderLayout.CENTER);
        c.add(Ui.label("Confirmed and completed rentals whose pick-up date is in the selected month.", Theme.font(11.5f), Theme.TEXT_3), BorderLayout.SOUTH);
        return c;
    }

    private JComponent requestsCard() {
        Card c = new Card(new BorderLayout(0, 12)).pad(20, 18, 16, 18);
        JPanel head = Ui.transparent(new BorderLayout());
        head.add(Ui.col(4, Ui.label("Pending requests", Theme.bold(16), Theme.TEXT),
                Ui.label("Confirm or reject. Oldest first.", Theme.font(13), Theme.TEXT_3)), BorderLayout.WEST);
        Btn all = new Btn("View all", Btn.Kind.GHOST).small();
        all.addActionListener(e -> shell.showBookings("Pending"));
        JPanel aw = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        aw.add(all);
        head.add(aw, BorderLayout.EAST);
        c.add(head, BorderLayout.NORTH);
        requests.setLayout(new BoxLayout(requests, BoxLayout.Y_AXIS));
        JScrollPane sp = Scroll.of(requests);
        c.add(sp, BorderLayout.CENTER);
        return c;
    }

    @Override public void refresh() {
        Async.run(StatsService::summary, s -> {
            pending.setText(String.valueOf(s.pending()));
            pendingNote.setText(s.pending() == 0 ? "All caught up" : "Click to review");
            onRoad.setText(String.valueOf(s.onRoad()));
            onRoadNote.setText(s.pickupsToday() + " pick-ups  ·  " + s.returnsToday() + " returns today");
            collected.setText(Fmt.money(s.collectedThisMonth()));
            collectedNote.setText("Net of refunds");
            fleet.setText(s.available() + " / " + s.fleet());
            fleetNote.setText((s.fleet() - s.available()) + " under maintenance");
            shell.setPendingCount(s.pending());
        });
        loadChart();
        loadRequests();
    }

    private void loadChart() {
        monthLabel.setText(Fmt.month(month));
        YearMonth m = month;
        Async.run(() -> StatsService.salesByType(m), list -> {
            stats = list;
            chart.setData(list);
            updateInsight();
        });
    }

    private void updateInsight() {
        int lead = chart.leader();
        if (lead < 0) { chartInsight.setText("No confirmed rentals in " + Fmt.month(month) + "."); return; }
        TypeStat t = stats.get(lead);
        BigDecimal total = BigDecimal.ZERO;
        int rentals = 0;
        for (TypeStat s : stats) { total = total.add(s.revenue()); rentals += s.rentals(); }
        boolean rev = metric.index() == 0;
        String share = rev
                ? (total.signum() == 0 ? "" : Math.round(t.revenue().doubleValue() * 100 / total.doubleValue()) + "% of " + Fmt.money(total))
                : (rentals == 0 ? "" : Math.round(t.rentals() * 100.0 / rentals) + "% of " + rentals + " rentals");
        chartInsight.setText("<html><b><font color='" + Theme.hex(Theme.TEXT) + "'>" + t.bodyType() + "</font></b> leads " + Fmt.month(month) + " with " + share + "</html>");
    }

    private void loadRequests() {
        Async.run(() -> BookingService.pending(20), list -> {
            requests.removeAll();
            if (list.isEmpty()) requests.add(Widgets.empty("check", "No pending requests", "New customer requests show up here."));
            for (Booking b : list) {
                JComponent r = requestRow(b);
                r.setAlignmentX(0f);
                requests.add(r);
                requests.add(Box.createVerticalStrut(8));
            }
            requests.revalidate();
            requests.repaint();
        });
    }

    private JComponent requestRow(Booking b) {
        Card row = new Card(new BorderLayout(10, 0)).fill(Theme.SURFACE_2).radius(12).pad(10, 10, 10, 10);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        JLabel ic = new JLabel(VehicleIcons.of(b.bodyType(), 54, b.electric()));
        row.add(ic, BorderLayout.WEST);
        row.add(Ui.col(2,
                Ui.label(b.customerName(), Theme.bold(13), Theme.TEXT),
                Ui.label(b.vehicleName() + "  ·  " + Fmt.money(b.total()), Theme.font(12), Theme.TEXT_2),
                Ui.label(Fmt.day(b.start()) + " → " + Fmt.day(b.end()) + "  ·  #" + b.id(), Theme.font(11.5f), Theme.TEXT_3)), BorderLayout.CENTER);
        JPanel act = Ui.transparent(new GridLayout(2, 1, 0, 4));
        Btn ok = new Btn("", Btn.Kind.SUBTLE, "check").small().tip("Confirm booking #" + b.id());
        Btn no = new Btn("", Btn.Kind.DANGER, "x").small().tip("Reject (cancel) booking #" + b.id());
        ok.addActionListener(e -> Async.act(() -> BookingService.confirm(b.id(), shell.user.id()), () -> {
            Toast.success("Booking #" + b.id() + " confirmed. The customer can now pay.");
            refresh();
        }));
        no.addActionListener(e -> {
            if (Modal.confirm(this, "Reject request #" + b.id() + "?", b.customerName() + " asked for " + b.vehicleName() + ". The booking will be marked Cancelled.", "Reject", true))
                Async.act(() -> BookingService.cancel(b.id(), shell.user.id()), () -> { Toast.info("Request #" + b.id() + " rejected."); refresh(); });
        });
        act.add(ok);
        act.add(no);
        row.add(act, BorderLayout.EAST);
        return row;
    }
}
