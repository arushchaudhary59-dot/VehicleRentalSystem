package vrs.ui.employee;

import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import javax.swing.*;
import vrs.db.DbErrors;
import vrs.model.Models.*;
import vrs.service.*;
import vrs.service.Session.User;
import vrs.ui.*;
import vrs.ui.comp.*;

/** Create a booking (walk-in / phone) or update an existing one. Shows a live price quote. */
final class BookingFormDialog {
    private BookingFormDialog() {}

    /** Wraps a vehicle for the drop-down. */
    private record VItem(Vehicle v) {
        @Override public String toString() {
            return v.name() + "  ·  " + v.reg() + "  ·  " + v.bodyType() + (v.status().equals("Available") ? "" : "  (" + v.status() + ")");
        }
    }

    static boolean open(Component parent, User emp, Booking existing) {
        boolean edit = existing != null;
        Modal m = new Modal(parent, edit ? "Edit booking #" + existing.id() : "New booking",
                edit ? existing.customerName() + "  ·  created " + Fmt.dateTime(existing.createdAt())
                     : "For a customer at the desk or on the phone.", 580);
        boolean[] saved = {false};

        Select<Customer> customer = new Select<>(List.of());
        Select<VItem> vehicle = new Select<>(List.of());
        DateField from = new DateField("Pick-up", edit ? existing.start() : LocalDate.now());
        DateField to = new DateField("Return", edit ? existing.end() : LocalDate.now().plusDays(2));
        Select<String> status = new Select<>(edit ? List.of("Pending", "Confirmed", "Completed", "Cancelled") : List.of("Confirmed", "Pending"));
        if (edit) status.setSelectedItem(existing.status());
        JLabel quote = Ui.label("Choose a vehicle and dates", Theme.font(13), Theme.TEXT_2);

        JPanel custRow = Ui.transparent(new BorderLayout(8, 0));
        custRow.add(customer, BorderLayout.CENTER);
        if (!edit) {
            Btn add = new Btn("New", Btn.Kind.SECONDARY, "plus");
            add.addActionListener(e -> {
                Customer c = newCustomer(m.getContentPane());
                if (c != null) {
                    List<Customer> l = new ArrayList<>();
                    for (int i = 0; i < customer.getItemCount(); i++) l.add(customer.getItemAt(i));
                    l.add(0, c);
                    customer.setItems(l);
                    customer.setSelectedItem(c);
                }
            });
            custRow.add(add, BorderLayout.EAST);
        } else customer.setEnabled(false);
        custRow.setPreferredSize(new Dimension(10, 44));
        m.field("Customer", custRow);
        m.field("Vehicle", vehicle);
        from.setPreferredSize(new Dimension(200, 58));
        to.setPreferredSize(new Dimension(200, 58));
        m.row(from, to);
        m.field("Status", status);
        Card qc = new Card(new BorderLayout()).fill(Theme.alpha(Theme.ACCENT, 18)).border(Theme.alpha(Theme.ACCENT, 70)).radius(12).pad(12, 14, 12, 14);
        qc.add(quote);
        qc.setAlignmentX(0f);
        qc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        m.body().add(qc);

        Runnable requote = () -> {
            VItem vi = vehicle.value();
            LocalDate a = from.value(), b = to.value();
            if (vi == null || a == null || b == null || !b.isAfter(a)) { quote.setText("Return must be after pick-up."); return; }
            boolean unchanged = edit && vi.v().id() == existing.vehicleId() && a.equals(existing.start()) && b.equals(existing.end());
            if (unchanged) {
                quote.setText("<html>Agreed price kept: <b>" + Fmt.money(existing.dailyRate()) + "</b>/day, "
                        + Fmt.percent(existing.discountPercent()) + " off  =  <b><font color='" + Theme.hex(Theme.TEXT) + "'>" + Fmt.money(existing.total()) + "</font></b>"
                        + "<br><font color='" + Theme.hex(Theme.TEXT_3) + "'>Changing vehicle or dates recalculates the price from the current pricing rules.</font></html>");
                return;
            }
            quote.setText("Calculating…");
            Async.run(() -> BookingService.quote(vi.v().id(), a, b), q -> quote.setText(
                    "<html>" + Fmt.money(q.dailyRate()) + "/day × " + Fmt.days(q.days()) + "  (tier " + q.tier() + ", "
                            + Fmt.percent(q.discountPercent()) + " off)  =  <b><font color='" + Theme.hex(Theme.TEXT) + "'>" + Fmt.money(q.total()) + "</font></b></html>"),
                    e -> quote.setText("<html><font color='" + Theme.hex(Theme.DANGER) + "'>" + DbErrors.friendly(e) + "</font></html>"));
        };
        vehicle.addActionListener(e -> requote.run());
        from.onChange(d -> { if (!to.value().isAfter(d)) to.setValue(d.plusDays(1), false); requote.run(); });
        to.onChange(d -> requote.run());

        Async.run(() -> new Object[]{StatsService.customers(), VehicleService.listAll()}, data -> {
            @SuppressWarnings("unchecked") List<Customer> cs = (List<Customer>) data[0];
            @SuppressWarnings("unchecked") List<Vehicle> vs = (List<Vehicle>) data[1];
            customer.setItems(cs);
            List<VItem> items = new ArrayList<>();
            VItem current = null;
            for (Vehicle v : vs) {
                boolean isCurrent = edit && v.id() == existing.vehicleId();
                if (v.status().equals("Available") || isCurrent) {
                    VItem vi = new VItem(v);
                    items.add(vi);
                    if (isCurrent) current = vi;
                }
            }
            vehicle.setItems(items);
            if (edit) {
                for (Customer c : cs) if (c.id() == existing.customerId()) customer.setSelectedItem(c);
                vehicle.setSelectedItem(current);
            }
            requote.run();
        });

        m.addCancel("Cancel");
        m.addButton(edit ? "Save changes" : "Create booking", Btn.Kind.PRIMARY, () -> {
            Customer c = customer.value();
            VItem vi = vehicle.value();
            if (c == null || vi == null) { m.showError("Choose a customer and a vehicle."); return; }
            String st = status.value();
            m.busy(true, "Saving…");
            Async.run(() -> {
                if (edit) { BookingService.update(existing.id(), vi.v().id(), from.value(), to.value(), st, emp.id()); return existing.id(); }
                return BookingService.create(c.id(), vi.v().id(), from.value(), to.value(), st, emp.id());
            }, id -> {
                saved[0] = true;
                m.dispose();
                Toast.success(edit ? "Booking #" + id + " updated." : "Booking #" + id + " created for " + c.name() + ".");
            }, e -> { m.busy(false, edit ? "Save changes" : "Create booking"); m.showError(DbErrors.friendly(e)); });
        });
        m.open();
        return saved[0];
    }

    /** Quick form to register a walk-in customer (no password; they can activate an online account later). */
    private static Customer newCustomer(Component parent) {
        Modal m = new Modal(parent, "New customer", "Registered at the desk. They can sign up later with the same e-mail and phone.", 480);
        JTextField name = Input.text("Full name", "user");
        JTextField email = Input.text("E-mail", "mail");
        JTextField phone = Input.text("Mobile (10 digits)", "phone");
        JTextField lic = Input.text("Driving licence no. (optional)", "id");
        m.field("Name", name);
        m.field("E-mail", email);
        m.row(Modal.labelled("Phone", phone), Modal.labelled("Licence", lic));
        Customer[] out = {null};
        m.addCancel("Cancel");
        m.addButton("Add customer", Btn.Kind.PRIMARY, () -> {
            m.busy(true, "Saving…");
            String n = name.getText(), em = email.getText(), ph = phone.getText(), li = lic.getText();
            Async.run(() -> StatsService.addCustomer(n, em, ph, li), id -> {
                out[0] = new Customer(id, n.trim(), em.trim().toLowerCase(), ph, li, false);
                m.dispose();
            }, e -> { m.busy(false, "Add customer"); m.showError(DbErrors.friendly(e)); });
        });
        m.open();
        return out[0];
    }
}
