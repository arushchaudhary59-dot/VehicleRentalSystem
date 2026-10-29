package vrs.ui.employee;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import vrs.db.DbErrors;
import vrs.service.EmployeeService;
import vrs.service.EmployeeService.Employee;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Manager only: list staff accounts, add a new employee, reset a password. */
final class StaffPage extends JPanel implements EmployeeShell.Refreshable {
    private final EmployeeShell shell;
    private final Tables.RowModel model = new Tables.RowModel(
            new String[]{"ID", "Employee", "Phone", "Designation", "Bookings handled"},
            new Class<?>[]{Integer.class, String.class, String.class, String.class, Integer.class});
    private final JTable table = Tables.create(model);
    private final JLabel count = Ui.label(" ", Theme.font(12.5f), Theme.TEXT_3);
    private final Btn reset = new Btn("Reset password", Btn.Kind.SECONDARY, "key").small();
    private List<Employee> all = new ArrayList<>();

    StaffPage(EmployeeShell shell) {
        super(new BorderLayout());
        this.shell = shell;
        setOpaque(false);
        setBorder(EmployeeShell.pagePad());
        Btn add = new Btn("Add employee", Btn.Kind.PRIMARY, "plus");
        add.addActionListener(e -> { if (addDialog()) refresh(); });
        JComponent h = EmployeeShell.header("Staff", "Employees who can log in to this console. New staff are added here by a Manager.", add);
        h.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        add(h, BorderLayout.NORTH);

        table.getColumnModel().getColumn(1).setCellRenderer(new Tables.TwoLine());
        table.getColumnModel().getColumn(2).setCellRenderer(new Tables.Muted());
        table.getColumnModel().getColumn(3).setCellRenderer(new Tables.BadgeCell());
        table.setRowHeight(52);
        Tables.widths(table, 50, 320, 160, 180, 140);
        add(Tables.inCard(table), BorderLayout.CENTER);

        JPanel bar = Ui.transparent(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(12, 2, 0, 0));
        bar.add(count, BorderLayout.WEST);
        JPanel actions = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(reset);
        bar.add(actions, BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);
        reset.addActionListener(e -> resetDialog());
        table.getSelectionModel().addListSelectionListener(e -> reset.setEnabled(selected() != null));
        reset.setEnabled(false);
    }

    @Override public void refresh() {
        Async.run(EmployeeService::all, list -> {
            all = list;
            List<Object[]> rows = new ArrayList<>();
            for (Employee e : list)
                rows.add(new Object[]{e.id(), e.name() + (e.id() == shell.user.id() ? "  (you)" : "") + "|" + e.email(),
                        e.phone() == null ? "—" : e.phone(), e.designation(), e.handled()});
            model.setRows(rows);
            count.setText(list.size() + " staff accounts");
        });
    }

    private Employee selected() {
        int r = table.getSelectedRow();
        if (r < 0) return null;
        int id = (Integer) model.getValueAt(table.convertRowIndexToModel(r), 0);
        for (Employee e : all) if (e.id() == id) return e;
        return null;
    }

    private boolean addDialog() {
        Modal m = new Modal(this, "Add employee", "Give the new employee their e-mail and this starting password.", 500);
        JTextField name = Input.text("Full name", "user");
        JTextField email = Input.text("Work e-mail", "mail");
        JTextField phone = Input.text("Mobile (optional)", "phone");
        Select<String> desig = new Select<>(EmployeeService.DESIGNATIONS);
        JPasswordField pass = Input.password("Starting password");
        JPasswordField confirm = Input.password("Confirm");
        m.field("Name", name);
        m.field("E-mail (used to log in)", email);
        m.row(Modal.labelled("Phone", phone), Modal.labelled("Designation", desig));
        m.row(Modal.labelled("Password", pass), Modal.labelled("Confirm", confirm));
        boolean[] ok = {false};
        m.addCancel("Cancel");
        m.addButton("Add employee", Btn.Kind.PRIMARY, () -> {
            String pw = new String(pass.getPassword());
            if (!pw.equals(new String(confirm.getPassword()))) { m.showError("Passwords do not match."); return; }
            String n = name.getText(), em = email.getText(), ph = phone.getText(), d = desig.value();
            m.busy(true, "Saving…");
            Async.run(() -> EmployeeService.add(n, em, ph, d, pw), id -> {
                ok[0] = true;
                m.dispose();
                Toast.success(n.trim() + " can now log in as an employee with " + em.trim().toLowerCase() + ".");
            }, e -> { m.busy(false, "Add employee"); m.showError(DbErrors.friendly(e)); });
        });
        m.open();
        return ok[0];
    }

    private void resetDialog() {
        Employee e = selected();
        if (e == null) return;
        Modal m = new Modal(this, "Reset password", e.name() + "  ·  " + e.email(), 460);
        JPasswordField pass = Input.password("New password");
        JPasswordField confirm = Input.password("Confirm");
        m.row(Modal.labelled("New password", pass), Modal.labelled("Confirm", confirm));
        m.addCancel("Cancel");
        m.addButton("Set password", Btn.Kind.PRIMARY, () -> {
            String pw = new String(pass.getPassword());
            if (!pw.equals(new String(confirm.getPassword()))) { m.showError("Passwords do not match."); return; }
            m.busy(true, "Saving…");
            Async.act(() -> EmployeeService.resetPassword(e.id(), pw), () -> {
                m.dispose();
                Toast.success("Password updated for " + e.name() + ".");
            }, err -> { m.busy(false, "Set password"); m.showError(DbErrors.friendly(err)); });
        });
        m.open();
    }
}
