package vrs.ui.employee;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import vrs.db.DbErrors;
import vrs.model.Models.*;
import vrs.model.VehicleTypes;
import vrs.service.VehicleService;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Fleet: add / edit vehicles and switch them between Available, Under Maintenance and Retired. */
final class VehiclesPage extends JPanel implements EmployeeShell.Refreshable {
    private final JTextField search = Input.text("Search make, model or registration", "search");
    private final Map<String, Chip> statusChips = new LinkedHashMap<>();
    private final Select<String> category = new Select<>(List.of("All categories", "Car", "Bike", "Scooter"));
    private final Tables.RowModel model = new Tables.RowModel(
            new String[]{"ID", "Vehicle", "Category", "Fuel", "Gearbox", "Seats", "Status", "Upcoming", "Bookings"},
            new Class<?>[]{Integer.class, String.class, String.class, String.class, String.class, Integer.class, String.class, Integer.class, Integer.class});
    private final JTable table = Tables.create(model);
    private final JLabel count = Ui.label(" ", Theme.font(12.5f), Theme.TEXT_3);
    private final Btn edit = new Btn("Edit", Btn.Kind.SECONDARY, "edit").small();
    private final Btn available = new Btn("Available", Btn.Kind.SUBTLE, "check").small();
    private final Btn maintenance = new Btn("Under maintenance", Btn.Kind.SECONDARY, "wrench").small();
    private final Btn retire = new Btn("Retire", Btn.Kind.DANGER, "x").small();
    private List<Vehicle> all = new ArrayList<>();

    VehiclesPage(EmployeeShell shell) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(EmployeeShell.pagePad());
        Btn add = new Btn("Add vehicle", Btn.Kind.PRIMARY, "plus");
        add.addActionListener(e -> { if (form(null)) refresh(); });
        Btn reload = new Btn("", Btn.Kind.SECONDARY, "refresh").tip("Reload");
        reload.addActionListener(e -> refresh());

        JPanel top = Ui.transparent(null);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JComponent h = EmployeeShell.header("Vehicles", "Add vehicles and mark them under maintenance. Unavailable vehicles disappear from customer search.", reload, add);
        h.setAlignmentX(0f);
        top.add(h);
        top.add(Box.createVerticalStrut(18));
        JPanel filters = Ui.transparent(new BorderLayout(12, 0));
        search.setPreferredSize(new Dimension(320, 40));
        filters.add(search, BorderLayout.WEST);
        JPanel chips = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 8, 3));
        for (String s : new String[]{"All", "Available", "Under Maintenance", "Retired"}) {
            Chip c = new Chip(s).accent(s.equals("All") ? Theme.ACCENT : Theme.statusColor(s));
            statusChips.put(s, c);
            chips.add(c);
            c.addActionListener(e -> { statusChips.forEach((k, v) -> v.setSelected(v == c)); apply(); });
        }
        statusChips.get("All").setSelected(true);
        filters.add(chips, BorderLayout.CENTER);
        category.setPreferredSize(new Dimension(170, 40));
        filters.add(category, BorderLayout.EAST);
        filters.setAlignmentX(0f);
        filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        top.add(filters);
        top.add(Box.createVerticalStrut(14));
        add(top, BorderLayout.NORTH);

        table.getColumnModel().getColumn(1).setCellRenderer(new Tables.VehicleCell());
        table.getColumnModel().getColumn(6).setCellRenderer(new Tables.BadgeCell());
        table.setRowHeight(52);
        Tables.widths(table, 46, 270, 90, 90, 100, 70, 170, 90, 90);
        add(Tables.inCard(table), BorderLayout.CENTER);

        JPanel bar = Ui.transparent(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(12, 2, 0, 0));
        bar.add(count, BorderLayout.WEST);
        JPanel actions = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(edit);
        actions.add(Ui.label("Set status:", Theme.font(12.5f), Theme.TEXT_3));
        actions.add(available);
        actions.add(maintenance);
        actions.add(retire);
        bar.add(actions, BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { apply(); }
            public void removeUpdate(DocumentEvent e) { apply(); }
            public void changedUpdate(DocumentEvent e) { apply(); }
        });
        category.addActionListener(e -> apply());
        table.getSelectionModel().addListSelectionListener(e -> buttons());
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && selected() != null && form(selected())) refresh(); }
        });
        edit.addActionListener(e -> { if (selected() != null && form(selected())) refresh(); });
        available.addActionListener(e -> setStatus("Available"));
        maintenance.addActionListener(e -> setStatus("Under Maintenance"));
        retire.addActionListener(e -> setStatus("Retired"));
        buttons();
    }

    @Override public void refresh() { Async.run(VehicleService::listAll, l -> { all = l; apply(); }); }

    private void apply() {
        String st = statusChips.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).findFirst().orElse("All");
        String cat = category.getSelectedIndex() <= 0 ? null : category.value();
        String q = search.getText().trim().toLowerCase();
        Integer sel = selected() == null ? null : selected().id();
        List<Object[]> rows = new ArrayList<>();
        int avail = 0;
        for (Vehicle v : all) {
            if (v.status().equals("Available")) avail++;
            if (!st.equals("All") && !v.status().equals(st)) continue;
            if (cat != null && !v.category().equals(cat)) continue;
            if (!q.isEmpty() && !(v.name() + " " + v.reg()).toLowerCase().contains(q)) continue;
            rows.add(new Object[]{v.id(), v.bodyType() + "|" + v.name() + "|" + v.reg() + "  ·  " + v.bodyType(), v.category(), v.fuel(),
                    v.transmission(), v.seats(), v.status(), v.upcomingBookings(), v.totalBookings()});
        }
        model.setRows(rows);
        count.setText(rows.size() + " shown  ·  " + avail + " of " + all.size() + " available");
        if (sel != null) for (int i = 0; i < table.getRowCount(); i++)
            if (sel.equals(table.getValueAt(i, 0))) { table.setRowSelectionInterval(i, i); break; }
        buttons();
    }

    private Vehicle selected() {
        int r = table.getSelectedRow();
        if (r < 0) return null;
        int id = (Integer) model.getValueAt(table.convertRowIndexToModel(r), 0);
        for (Vehicle v : all) if (v.id() == id) return v;
        return null;
    }

    private void buttons() {
        Vehicle v = selected();
        String s = v == null ? "" : v.status();
        edit.setEnabled(v != null);
        available.setEnabled(v != null && !s.equals("Available"));
        maintenance.setEnabled(v != null && !s.equals("Under Maintenance"));
        retire.setEnabled(v != null && !s.equals("Retired"));
    }

    private void setStatus(String status) {
        Vehicle v = selected();
        if (v == null) return;
        if (!status.equals("Available") && v.upcomingBookings() > 0
                && !Modal.confirm(this, v.name() + " has " + v.upcomingBookings() + " upcoming booking(s)",
                "Existing bookings stay as they are; you may want to move them to another vehicle (Bookings → Edit). "
                        + "New customers will not see this vehicle.", "Set " + status.toLowerCase(), true)) return;
        Async.act(() -> VehicleService.setStatus(v.id(), status), () -> {
            Toast.success(v.name() + " (" + v.reg() + ") is now " + status.toLowerCase() + ".");
            refresh();
        });
    }

    /** Add (v == null) or edit a vehicle. */
    private boolean form(Vehicle v) {
        boolean editing = v != null;
        Modal m = new Modal(this, editing ? "Edit " + v.name() : "Add vehicle", editing ? v.reg() : "New vehicles start as Available.", 540);
        JTextField reg = Input.text("e.g. TN 23 AB 1234", "id");
        JTextField make = Input.text("e.g. Hyundai", null);
        JTextField mdl = Input.text("e.g. Creta", null);
        Select<Category> cat = new Select<>(List.of());
        Select<String> body = new Select<>(VehicleTypes.CAR);
        Select<String> fuel = new Select<>(VehicleTypes.FUELS);
        Select<String> trans = new Select<>(VehicleTypes.TRANSMISSIONS);
        JTextField seats = Input.text("5", "seat");
        JLabel preview = new JLabel(VehicleIcons.of("Hatchback", 150, false));
        m.body().add(Ui.left(preview));
        m.body().add(Box.createVerticalStrut(10));
        m.field("Registration number", reg);
        m.row(Modal.labelled("Make", make), Modal.labelled("Model", mdl));
        m.row(Modal.labelled("Category", cat), Modal.labelled("Type", body));
        m.row(Modal.labelled("Fuel", fuel), Modal.labelled("Transmission", trans), Modal.labelled("Seats", seats));

        Runnable syncBody = () -> {
            Category c = cat.value();
            List<String> types = VehicleTypes.forCategory(c == null ? null : c.name());
            String keep = body.value();
            body.setItems(types);
            if (keep != null && types.contains(keep)) body.setSelectedItem(keep);
            if (c != null && !editing) {
                boolean two = !c.name().equalsIgnoreCase("Car");
                seats.setText(two ? "2" : "5");
                if (c.name().equalsIgnoreCase("Scooter")) trans.setSelectedItem("Automatic");
                if (c.name().equalsIgnoreCase("Bike")) trans.setSelectedItem("Manual");
            }
        };
        Runnable syncPreview = () -> preview.setIcon(VehicleIcons.of(body.value(), 150, "Electric".equals(fuel.value())));
        cat.addActionListener(e -> { syncBody.run(); syncPreview.run(); });
        body.addActionListener(e -> syncPreview.run());
        fuel.addActionListener(e -> syncPreview.run());
        if (editing) {
            reg.setText(v.reg()); make.setText(v.make()); mdl.setText(v.model());
            fuel.setSelectedItem(v.fuel()); trans.setSelectedItem(v.transmission()); seats.setText(String.valueOf(v.seats()));
        } else seats.setText("5");
        Async.run(VehicleService::categories, cs -> {
            cat.setItems(cs);
            if (editing) for (Category c : cs) if (c.id() == v.categoryId()) cat.setSelectedItem(c);
            syncBody.run();
            if (editing) body.setSelectedItem(v.bodyType());
            syncPreview.run();
            if (editing && v.totalBookings() > 0) cat.setEnabled(false);
        });

        boolean[] saved = {false};
        m.addCancel("Cancel");
        m.addButton(editing ? "Save" : "Add vehicle", Btn.Kind.PRIMARY, () -> {
            int st;
            try { st = Integer.parseInt(seats.getText().trim()); } catch (NumberFormatException ex) { m.showError("Seats must be a number."); return; }
            String r = reg.getText(), mk = make.getText(), md = mdl.getText(), b = body.value(), f = fuel.value(), t = trans.value();
            Category c = cat.value();
            m.busy(true, "Saving…");
            Async.run(() -> {
                if (editing) { VehicleService.update(v.id(), r, mk, md, c, b, f, t, st); return v.id(); }
                return VehicleService.add(r, mk, md, c, b, f, t, st);
            }, id -> {
                saved[0] = true;
                m.dispose();
                Toast.success(editing ? "Vehicle updated." : "Vehicle #" + id + " added and available for booking.");
            }, e -> { m.busy(false, editing ? "Save" : "Add vehicle"); m.showError(DbErrors.friendly(e)); });
        });
        m.open();
        return saved[0];
    }
}
