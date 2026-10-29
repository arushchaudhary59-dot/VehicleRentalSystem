package vrs.ui.customer;

import java.awt.*;
import java.awt.geom.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import javax.swing.*;
import vrs.model.Models.Offer;
import vrs.model.VehicleTypes;
import vrs.service.Session.User;
import vrs.service.VehicleService;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Search: pick dates, filter by type / fuel / transmission, then book. */
public final class ExplorePage extends JPanel {
    private final User user;
    private final CustomerShell shell;
    private final DateField from = new DateField("Pick-up", LocalDate.now().plusDays(1));
    private final DateField to = new DateField("Return", LocalDate.now().plusDays(4));
    private final JLabel duration = Ui.label("", Theme.bold(13), Theme.ACCENT_HI);
    private final Map<String, Chip> typeChips = new LinkedHashMap<>();
    private final Map<String, Chip> fuelChips = new LinkedHashMap<>();
    private final Map<String, Chip> transChips = new LinkedHashMap<>();
    private final Select<String> sort = new Select<>(List.of("Price: low to high", "Price: high to low", "Name A–Z", "Most seats"));
    private final JLabel resultTitle = Ui.label("", Theme.bold(18), Theme.TEXT);
    private final JLabel resultSub = Ui.label("", Theme.font(13), Theme.TEXT_3);
    private final Widgets.Grid grid = new Widgets.Grid(270, 318, 18);
    private final JPanel results = Ui.transparent(new BorderLayout());
    private List<Offer> offers = new ArrayList<>();
    private boolean loading;

    ExplorePage(User user, CustomerShell shell) {
        super(new BorderLayout());
        this.user = user;
        this.shell = shell;
        setOpaque(false);

        Page page = new Page();
        page.setBorder(BorderFactory.createEmptyBorder(26, 32, 32, 32));
        page.add(hero(), 22);
        page.add(typeRow(), 14);
        page.add(filterRow(), 24);
        JPanel head = Ui.transparent(new BorderLayout());
        head.add(Ui.col(4, resultTitle, resultSub), BorderLayout.WEST);
        page.add(head, 16);
        results.add(grid);
        page.add(results, 0);
        add(Scroll.of(page));

        from.setMin(LocalDate.now());
        to.setMin(from.value().plusDays(1));
        to.setRangeFrom(from.value());
        from.onChange(d -> {
            if (!to.value().isAfter(d)) to.setValue(d.plusDays(1), false);
            to.setMin(d.plusDays(1));
            to.setRangeFrom(d);
            search();
        });
        to.onChange(d -> search());
        sort.addActionListener(e -> render());
    }

    /** Called when the page becomes visible. */
    void refresh() { search(); }

    private JComponent hero() {
        Card c = new Card(new BorderLayout(0, 18)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = Theme.g2(g);
                g2.setClip(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16));
                g2.setPaint(new RadialGradientPaint(new Point2D.Double(getWidth() * 0.85, 0), getWidth() * 0.5f,
                        new float[]{0, 1}, new Color[]{Theme.alpha(Theme.ACCENT, 40), Theme.alpha(Theme.ACCENT, 0)}));
                g2.fillRect(0, 0, getWidth(), getHeight());
                VehicleIcons.of("SUV", 150, Theme.alpha(Theme.TEXT, 18), false).paintIcon(this, g2, getWidth() - 190, 18);
                g2.dispose();
            }
        };
        c.pad(26, 28, 26, 28);
        JLabel h = Ui.label("Find a ride for your dates", Theme.bold(28), Theme.TEXT);
        JLabel s = Ui.label("Cars, bikes and scooters. Prices drop for longer rentals.", Theme.font(14.5f), Theme.TEXT_2);
        c.add(Ui.col(6, h, s), BorderLayout.NORTH);

        JPanel row = Ui.transparent(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, 0, 12);
        g.weightx = 1;
        from.setPreferredSize(new Dimension(220, 58));
        to.setPreferredSize(new Dimension(220, 58));
        row.add(from, g);
        row.add(to, g);
        g.weightx = 0;
        JPanel dur = new Card(new GridBagLayout()).fill(Theme.alpha(Theme.ACCENT, 22)).border(Theme.alpha(Theme.ACCENT, 90)).radius(12);
        dur.setPreferredSize(new Dimension(150, 58));
        duration.setIcon(Icons.of("clock", 16, Theme.ACCENT_HI));
        duration.setIconTextGap(8);
        dur.add(duration);
        row.add(dur, g);
        g.insets = new Insets(0, 0, 0, 0);
        Btn go = new Btn("Search", Btn.Kind.PRIMARY, "search");
        go.setPreferredSize(new Dimension(130, 58));
        go.addActionListener(e -> search());
        row.add(wrapFixed(go, 130, 58), g);
        c.add(row, BorderLayout.CENTER);
        return c;
    }

    private static JComponent wrapFixed(JComponent b, int w, int h) {
        JPanel p = Ui.transparent(new BorderLayout());
        p.add(b);
        p.setPreferredSize(new Dimension(w, h));
        return p;
    }

    private JComponent typeRow() {
        JPanel p = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ButtonGroup bg = new ButtonGroup();
        Chip all = new Chip("All vehicles").icon("grid");
        all.setSelected(true);
        typeChips.put("All", all);
        bg.add(all);
        p.add(all);
        for (String t : VehicleTypes.ALL) {
            Chip c = new Chip(t).vehicle(t).accent(Theme.typeColor(t));
            typeChips.put(t, c);
            bg.add(c);
            p.add(c);
        }
        for (Chip c : typeChips.values()) c.addActionListener(e -> render());
        ((FlowLayout) p.getLayout()).setHgap(8);
        p.setBorder(BorderFactory.createEmptyBorder(0, -8, 0, 0));
        return p;
    }

    private JComponent filterRow() {
        JPanel left = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setBorder(BorderFactory.createEmptyBorder(0, -8, 0, 0));
        left.add(Ui.label("Fuel", Theme.bold(12), Theme.TEXT_3));
        for (String f : VehicleTypes.FUELS) {
            Chip c = new Chip(f).icon(f.equals("Electric") ? "bolt" : "fuel");
            fuelChips.put(f, c);
            c.addActionListener(e -> render());
            left.add(c);
        }
        left.add(Box.createHorizontalStrut(14));
        left.add(Ui.label("Transmission", Theme.bold(12), Theme.TEXT_3));
        for (String t : VehicleTypes.TRANSMISSIONS) {
            Chip c = new Chip(t).icon("gear");
            transChips.put(t, c);
            c.addActionListener(e -> render());
            left.add(c);
        }
        JPanel row = Ui.transparent(new BorderLayout());
        row.add(left, BorderLayout.CENTER);
        sort.setPreferredSize(new Dimension(200, 36));
        JPanel r = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        r.add(sort);
        row.add(r, BorderLayout.EAST);
        return row;
    }

    private void search() {
        LocalDate a = from.value(), b = to.value();
        int days = (int) ChronoUnit.DAYS.between(a, b);
        duration.setText(Fmt.days(days));
        loading = true;
        results.removeAll();
        results.add(Widgets.loading("Checking availability…"));
        results.revalidate();
        results.repaint();
        resultTitle.setText("Searching…");
        resultSub.setText(Fmt.day(a) + "  →  " + Fmt.day(b));
        Async.run(() -> VehicleService.search(a, b), list -> {
            loading = false;
            offers = list;
            render();
        }, e -> {
            loading = false;
            results.removeAll();
            results.add(Widgets.empty("alert", "Could not load vehicles", vrs.db.DbErrors.friendly(e)));
            resultTitle.setText("Something went wrong");
            results.revalidate();
            results.repaint();
        });
    }

    /** Applies the chip filters and sort on the loaded offers (no database call). */
    private void render() {
        if (loading) return;
        String type = typeChips.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).findFirst().orElse("All");
        Set<String> fuels = selected(fuelChips), trans = selected(transChips);
        List<Offer> shown = new ArrayList<>();
        for (Offer o : offers) {
            if (!type.equals("All") && !type.equals(o.vehicle().bodyType())) continue;
            if (!fuels.isEmpty() && !fuels.contains(o.vehicle().fuel())) continue;
            if (!trans.isEmpty() && !trans.contains(o.vehicle().transmission())) continue;
            shown.add(o);
        }
        Comparator<Offer> cmp = switch (sort.getSelectedIndex()) {
            case 1 -> Comparator.comparing(Offer::total).reversed();
            case 2 -> Comparator.comparing(o -> o.vehicle().name());
            case 3 -> Comparator.comparing((Offer o) -> o.vehicle().seats()).reversed().thenComparing(Offer::total);
            default -> Comparator.comparing(Offer::total);
        };
        shown.sort(cmp);
        grid.removeAll();
        for (Offer o : shown) grid.add(new VehicleCard(o, () -> book(o)));
        results.removeAll();
        if (shown.isEmpty()) results.add(Widgets.empty("search", "No vehicles match", "Try other dates or remove a filter."));
        else results.add(grid);
        int days = offers.isEmpty() ? (int) ChronoUnit.DAYS.between(from.value(), to.value()) : offers.get(0).days();
        resultTitle.setText(shown.size() + (shown.size() == 1 ? " vehicle" : " vehicles") + " available");
        resultSub.setText(Fmt.day(from.value()) + "  →  " + Fmt.day(to.value()) + "   ·   " + Fmt.days(days)
                + (type.equals("All") ? "" : "   ·   " + type));
        results.revalidate();
        results.repaint();
    }

    private static Set<String> selected(Map<String, Chip> chips) {
        Set<String> s = new HashSet<>();
        chips.forEach((k, c) -> { if (c.isSelected()) s.add(k); });
        return s;
    }

    private void book(Offer o) {
        if (BookingDialog.open(this, user, o, from.value(), to.value())) {
            search();
            if (Modal.confirm(this, "Request sent", "Our team will review your request shortly. You can pay as soon as it is confirmed."
                    + "<br><br>Open <b>My bookings</b> now?", "Open My bookings", false))
                shell.show("bookings");
        }
    }
}
