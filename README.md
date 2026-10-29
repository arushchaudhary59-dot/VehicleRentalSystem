# Vehicle Rental System — Java Swing + JDBC + MySQL

A desktop application for the DBMS project. Customers sign up, search vehicles for their dates, and request bookings. Employees confirm, edit, complete, cancel or delete bookings, record payments, manage the fleet, read the query log, and explore the live ER diagram.

## 1. First-time setup (about 5 minutes)

1. **Import the database.** This replaces the old version cleanly. In Terminal:
   ```
   mysql -u root -p < ~/Desktop/DBMS_PROJECT/VehicleRentalSystem/sql/vehicle_rental_system.sql
   ```
   The last lines it prints are row counts. EMPLOYEE should show 2 and VEHICLE 53.
2. **Open the project in IntelliJ.** The `mysql-connector-j` jar must already be in **External Libraries**; you added it earlier. No other library is needed.
3. **Run it.** Right-click `src/Main.java` → **Run 'Main.main()'**.
4. **Enter your MySQL password.** The first time, a *Database connection* window opens. Enter your MySQL user and password, click **Test**, then **Save & connect**. The settings are saved in `db.properties` in the project folder. You can reopen this window later with the database icon on the login card.

## 2. Demo logins

| Role | E-mail | Password |
|---|---|---|
| Employee (Manager) | `admin@vrs.in` | `Admin@123` |
| Employee (Rental Operator) | `desk@vrs.in` | `Staff@123` |
| Customer | any sample customer, e.g. `kavya.subramanian@gmail.com` | `Password@123` |

New customers can create an account with **Sign up**. Employees cannot sign up: a **Manager** adds them on the **Staff** page.

## 3. Workflow

```
Customer: sign up / log in → Explore (dates, type, fuel, gearbox) → Book → request is PENDING
Employee: Dashboard / Bookings → Confirm (or Reject)            → booking is CONFIRMED
Customer: My bookings → Pay (UPI / Card / Net Banking)          → PAYMENT row "Success"
Employee: Bookings → Complete when the vehicle is returned       → booking is COMPLETED
Either side can cancel before pick-up → CANCELLED (+ automatic "Refunded" payment row if money was paid)
```

* A **pending** request already blocks the vehicle for those dates.
* Two bookings for the same vehicle can never overlap. The BOOKING trigger enforces this inside a transaction, using the report's rule `ExistingStart < NewEnd AND ExistingEnd > NewStart`.
* Price = days × daily rate of the matching PRICING_RULE tier × (1 − discount). MySQL computes it with the same formula as the CHECK constraint.

## 4. Employee screens

| Screen | What it does | SQL used |
|---|---|---|
| Dashboard | Pending / on-road / collected / fleet cards, a **"which vehicle type sells most"** chart per month (revenue or rentals), and confirm/reject buttons | SELECT … GROUP BY BodyType |
| Bookings | **CRUD**: New booking (Create), list with filters (Read), Edit / Confirm / Complete / Cancel (Update), Delete | INSERT, SELECT, UPDATE, DELETE |
| Payments | All payments and refunds, totals, and **Record payment** for desk (cash) payments | SELECT, INSERT |
| Vehicles | Add / edit vehicles, set Available / Under Maintenance / Retired | INSERT, UPDATE |
| Query Log | Every SQL statement the app ran, with bound values, time in ms, rows, errors, and transactions (START / COMMIT / ROLLBACK) | QUERY_LOG table |
| Staff *(Managers only)* | Add a new employee (name, e-mail, designation, starting password) and reset an employee's password | INSERT, UPDATE on EMPLOYEE |
| ER Diagram | Interactive Chen diagram. Zoom, pan, drag, hover to highlight, click an entity for live columns, keys and row count; **Export PNG** | INFORMATION_SCHEMA |

## 5. What changed in the database (version 2)

Nothing from version 1 was removed. Every addition is marked `[v2]` in the SQL file.

| Change | Why |
|---|---|
| `CUSTOMER.PasswordSalt`, `PasswordHash` | Login. Stores SHA-256(salt + password), never the password itself. NULL means a walk-in customer added by staff with no online account yet; they can activate it by signing up with the same e-mail and phone. |
| New table `EMPLOYEE` | Staff login (EmployeeID, FullName, Email, Phone, Designation, PasswordSalt, PasswordHash) |
| `BOOKING.HandledBy` → `EMPLOYEE` | Records which employee confirmed, edited or cancelled a booking. New ER relationship **EMPLOYEE 1 — HANDLES — N BOOKING** |
| `VEHICLE.BodyType`, `FuelType`, `Transmission`, `Seats` | A different symbol per vehicle type (Hatchback, Sedan, SUV, MUV, Cruiser, Sports, Commuter, Scooter) and the search filters |
| New table `QUERY_LOG` | Audit table for the Query Log screen. It has no foreign keys on purpose, so log rows survive even if a user is deleted. |
| 3 extra cars (Verna, Dzire, Carens) | So that Sedan and MUV have more than one choice |

**Note for the normalisation chapter:** BodyType implies the category (for example, Sedan means Car). That is a functional dependency outside the key, accepted as a deliberate trade-off for display and filtering. The app only offers body types that match the chosen category.

The updated ER diagram is in `er/`. The `.drawio` file uses your original styles, and the `.png` is ready for the report.

## 6. Project layout

```
src/Main.java                  launcher
src/vrs/App.java               window + screen switching
src/vrs/db/                    Db (every JDBC call + logging), Row, DbConfig, QueryLogger, DbErrors
src/vrs/service/               AuthService, VehicleService, BookingService, PaymentService,
                               StatsService, LogService, SchemaService, Session
src/vrs/model/                 records (Vehicle, Booking, Payment, …)
src/vrs/ui/                    Theme, icons, vehicle symbols, reusable components (comp/)
src/vrs/ui/auth/               login / sign-up
src/vrs/ui/customer/           Explore, booking dialog, My bookings, payment
src/vrs/ui/employee/           dashboard, bookings, payments, vehicles, query log, ER diagram
sql/vehicle_rental_system.sql  database script (version 2)
er/                            updated ER diagram (.drawio + .png)
```

## 7. Troubleshooting

| Message | Fix |
|---|---|
| Cannot reach MySQL | Start the MySQL server (System Settings → MySQL, or `brew services start mysql`). |
| MySQL refused the login | Wrong user or password. Click the database icon on the login card. |
| The database does not exist yet | Run step 1. |
| …missing tables/columns of version 2 | You imported the old script. Run step 1 again with the new file. |
| `ClassNotFoundException` / No suitable driver | The connector jar is not added as a library (Project Structure → Libraries). |
