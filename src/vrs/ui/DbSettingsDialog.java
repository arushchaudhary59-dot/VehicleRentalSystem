package vrs.ui;

import java.awt.*;
import javax.swing.*;
import vrs.db.*;
import vrs.ui.comp.*;

/** Lets the user enter the MySQL host / user / password. Saved to db.properties. */
public final class DbSettingsDialog {
    private DbSettingsDialog() {}

    /** Returns true when the settings were saved. */
    public static boolean open(Component parent) {
        DbConfig c = Db.config();
        Modal m = new Modal(parent, "Database connection", "Where is your MySQL server?", 480);
        JTextField host = Input.text("localhost", "db");
        host.setText(c.host);
        JTextField port = Input.text("3306", null);
        port.setText(String.valueOf(c.port));
        JTextField db = Input.text("vehicle_rental_db", null);
        db.setText(c.database);
        JTextField user = Input.text("root", "user");
        user.setText(c.user);
        JPasswordField pass = Input.password("MySQL password");
        pass.setText(c.password);
        m.row(Modal.labelled("Host", host), Modal.labelled("Port", port));
        m.field("Database", db);
        m.row(Modal.labelled("User", user), Modal.labelled("Password", pass));
        JLabel status = Ui.label(" ", Theme.font(12.5f), Theme.TEXT_2);
        m.body().add(Ui.left(status));

        boolean[] saved = {false};
        java.util.function.Supplier<DbConfig> read = () -> {
            DbConfig n = new DbConfig();
            n.host = host.getText().trim();
            n.port = Integer.parseInt(port.getText().trim());
            n.database = db.getText().trim();
            n.user = user.getText().trim();
            n.password = new String(pass.getPassword());
            return n;
        };
        m.addButton("Test", Btn.Kind.SECONDARY, () -> {
            DbConfig n;
            try { n = read.get(); } catch (NumberFormatException ex) { m.showError("Port must be a number."); return; }
            status.setForeground(Theme.TEXT_2);
            status.setText("Connecting…");
            Async.run(() -> Db.test(n), v -> {
                status.setForeground(Theme.ACCENT_HI);
                status.setText("Connected to MySQL " + v);
                m.showError(null);
            }, e -> { status.setText(" "); m.showError(DbErrors.friendly(e)); });
        });
        m.addButton("Save & connect", Btn.Kind.PRIMARY, () -> {
            DbConfig n;
            try { n = read.get(); } catch (NumberFormatException ex) { m.showError("Port must be a number."); return; }
            m.busy(true, "Connecting…");
            Async.run(() -> { Db.test(n); n.save(); return n; }, ok -> {
                Db.useConfig(ok);
                saved[0] = true;
                m.dispose();
            }, e -> { m.busy(false, "Save & connect"); m.showError(DbErrors.friendly(e)); });
        });
        m.open();
        return saved[0];
    }
}
