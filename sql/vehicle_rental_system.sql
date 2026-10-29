-- =====================================================================
--  VEHICLE RENTAL SYSTEM  -  MySQL 8.0.16+ database script
--  DBMS Project (VIT)  -  Database Analysis and Design Phase
--
--  Contains : schema (8 tables), integrity constraints, triggers,
--             and ~300 rows of consistent sample data.
--  Version 2 (application phase) - everything from version 1 is kept;
--             additions are marked  [v2]  :
--             * CUSTOMER.PasswordSalt / PasswordHash   (customer login)
--             * VEHICLE.BodyType / FuelType / Transmission / Seats
--             * EMPLOYEE table + BOOKING.HandledBy      (staff login, who
--               confirmed / cancelled a booking)
--             * QUERY_LOG table                         (query log screen)
--  Demo logins (passwords are stored as salted SHA-256 hashes):
--             customers : any sample customer e-mail / Password@123
--             employees : admin@vrs.in / Admin@123
--                         desk@vrs.in  / Staff@123
--  How to run:
--     mysql -u root -p < vehicle_rental_system.sql
--     (or open the file in MySQL Workbench and run the whole script)
--  Notes    : * Requires MySQL 8.0.16 or later (CHECK constraints are
--               enforced from that version).
--             * Re-runnable: tables are dropped and recreated.
--             * Rental interval convention: [StartDate, EndDate)
--               i.e. a booking may start on the day another one ends.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS vehicle_rental_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vehicle_rental_db;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS QUERY_LOG;                      -- [v2]
DROP TABLE IF EXISTS PAYMENT;
DROP TABLE IF EXISTS BOOKING;
DROP TABLE IF EXISTS PRICING_RULE;
DROP TABLE IF EXISTS VEHICLE;
DROP TABLE IF EXISTS CUSTOMER;
DROP TABLE IF EXISTS EMPLOYEE;                       -- [v2]
DROP TABLE IF EXISTS VEHICLE_CATEGORY;
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 1. TABLES
-- =====================================================================

CREATE TABLE CUSTOMER (
    CustomerID        INT          NOT NULL AUTO_INCREMENT,
    FullName          VARCHAR(100) NOT NULL,
    Email             VARCHAR(150) NOT NULL,
    Phone             VARCHAR(20)  NOT NULL,
    DrivingLicenceNo  VARCHAR(50)  NULL,
    -- [v2] login. SHA-256(PasswordSalt + password) as 64 hex characters.
    -- NULL = record created at the counter by staff, no online account yet.
    PasswordSalt      CHAR(16)     NULL,
    PasswordHash      CHAR(64)     NULL,
    CONSTRAINT pk_customer         PRIMARY KEY (CustomerID),
    CONSTRAINT uq_customer_email   UNIQUE (Email),
    CONSTRAINT uq_customer_phone   UNIQUE (Phone),
    CONSTRAINT uq_customer_licence UNIQUE (DrivingLicenceNo),
    CONSTRAINT chk_customer_email  CHECK (Email LIKE '_%@_%._%'),
    CONSTRAINT chk_customer_login  CHECK ((PasswordSalt IS NULL) = (PasswordHash IS NULL))   -- [v2]
) ENGINE=InnoDB;

-- [v2] Staff accounts. Employees cannot sign up from the app; they are
--      created here by the administrator.
CREATE TABLE EMPLOYEE (
    EmployeeID    INT          NOT NULL AUTO_INCREMENT,
    FullName      VARCHAR(100) NOT NULL,
    Email         VARCHAR(150) NOT NULL,
    Phone         VARCHAR(20)  NULL,
    Designation   VARCHAR(30)  NOT NULL DEFAULT 'Rental Operator',
    PasswordSalt  CHAR(16)     NOT NULL,
    PasswordHash  CHAR(64)     NOT NULL,
    CONSTRAINT pk_employee          PRIMARY KEY (EmployeeID),
    CONSTRAINT uq_employee_email    UNIQUE (Email),
    CONSTRAINT chk_employee_email   CHECK (Email LIKE '_%@_%._%'),
    CONSTRAINT chk_employee_desig   CHECK (Designation IN ('Manager', 'Rental Operator'))
) ENGINE=InnoDB;

CREATE TABLE VEHICLE_CATEGORY (
    CategoryID    INT          NOT NULL AUTO_INCREMENT,
    CategoryName  VARCHAR(50)  NOT NULL,
    Description   VARCHAR(255) NULL,
    CONSTRAINT pk_vehicle_category PRIMARY KEY (CategoryID),
    CONSTRAINT uq_category_name    UNIQUE (CategoryName)
) ENGINE=InnoDB;

CREATE TABLE VEHICLE (
    VehicleID          INT         NOT NULL AUTO_INCREMENT,
    RegistrationNo     VARCHAR(30) NOT NULL,
    Make               VARCHAR(60) NOT NULL,
    Model              VARCHAR(60) NOT NULL,
    CategoryID         INT         NOT NULL,
    OperationalStatus  VARCHAR(20) NOT NULL DEFAULT 'Available',
    -- [v2] descriptive attributes used for vehicle symbols and search filters
    BodyType           VARCHAR(20) NOT NULL,
    FuelType           VARCHAR(10) NOT NULL,
    Transmission       VARCHAR(10) NOT NULL,
    Seats              TINYINT     NOT NULL,
    CONSTRAINT pk_vehicle      PRIMARY KEY (VehicleID),
    CONSTRAINT uq_vehicle_reg  UNIQUE (RegistrationNo),
    CONSTRAINT fk_vehicle_category FOREIGN KEY (CategoryID)
        REFERENCES VEHICLE_CATEGORY (CategoryID)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_vehicle_status
        CHECK (OperationalStatus IN ('Available', 'Under Maintenance', 'Retired')),
    CONSTRAINT chk_vehicle_body                                                   -- [v2]
        CHECK (BodyType IN ('Hatchback', 'Sedan', 'SUV', 'MUV',
                            'Cruiser', 'Sports', 'Commuter', 'Scooter')),
    CONSTRAINT chk_vehicle_fuel  CHECK (FuelType IN ('Petrol', 'Diesel', 'Electric', 'CNG')),
    CONSTRAINT chk_vehicle_trans CHECK (Transmission IN ('Manual', 'Automatic')),
    CONSTRAINT chk_vehicle_seats CHECK (Seats BETWEEN 1 AND 12)
) ENGINE=InnoDB;

CREATE TABLE PRICING_RULE (
    PricingRuleID    INT           NOT NULL AUTO_INCREMENT,
    CategoryID       INT           NOT NULL,
    MinDays          INT           NOT NULL,
    MaxDays          INT           NULL,          -- NULL = open-ended (e.g. "7 days or more")
    DailyRate        DECIMAL(10,2) NOT NULL,
    DiscountPercent  DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
    EffectiveFrom    DATE          NOT NULL,
    EffectiveTo      DATE          NULL,          -- NULL = currently in force
    CONSTRAINT pk_pricing_rule PRIMARY KEY (PricingRuleID),
    CONSTRAINT fk_pricing_category FOREIGN KEY (CategoryID)
        REFERENCES VEHICLE_CATEGORY (CategoryID)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_rule_mindays  CHECK (MinDays >= 1),
    CONSTRAINT chk_rule_maxdays  CHECK (MaxDays IS NULL OR MaxDays >= MinDays),
    CONSTRAINT chk_rule_rate     CHECK (DailyRate >= 0),
    CONSTRAINT chk_rule_discount CHECK (DiscountPercent BETWEEN 0 AND 100),
    CONSTRAINT chk_rule_dates    CHECK (EffectiveTo IS NULL OR EffectiveTo >= EffectiveFrom)
) ENGINE=InnoDB;

CREATE TABLE BOOKING (
    BookingID               INT           NOT NULL AUTO_INCREMENT,
    CustomerID              INT           NOT NULL,
    VehicleID               INT           NOT NULL,
    PricingRuleID           INT           NULL,
    StartDate               DATE          NOT NULL,
    EndDate                 DATE          NOT NULL,     -- exclusive: [StartDate, EndDate)
    BookingStatus           VARCHAR(20)   NOT NULL DEFAULT 'Pending',
    AgreedDailyRate         DECIMAL(10,2) NOT NULL,
    AppliedDiscountPercent  DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
    TotalAmount             DECIMAL(10,2) NOT NULL,
    CreatedAt               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    HandledBy               INT           NULL,         -- [v2] employee who confirmed / cancelled / created it
    CONSTRAINT pk_booking PRIMARY KEY (BookingID),
    CONSTRAINT fk_booking_customer FOREIGN KEY (CustomerID)
        REFERENCES CUSTOMER (CustomerID)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_booking_vehicle FOREIGN KEY (VehicleID)
        REFERENCES VEHICLE (VehicleID)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_booking_rule FOREIGN KEY (PricingRuleID)
        REFERENCES PRICING_RULE (PricingRuleID)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_booking_employee FOREIGN KEY (HandledBy)                       -- [v2]
        REFERENCES EMPLOYEE (EmployeeID)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_booking_dates    CHECK (EndDate > StartDate),
    CONSTRAINT chk_booking_status   CHECK (BookingStatus IN ('Pending', 'Confirmed', 'Completed', 'Cancelled')),
    CONSTRAINT chk_booking_rate     CHECK (AgreedDailyRate >= 0),
    CONSTRAINT chk_booking_discount CHECK (AppliedDiscountPercent BETWEEN 0 AND 100),
    CONSTRAINT chk_booking_total    CHECK (TotalAmount >= 0),
    -- TotalAmount = Duration x AgreedDailyRate x (1 - Discount/100)
    CONSTRAINT chk_booking_formula  CHECK (TotalAmount =
        ROUND(DATEDIFF(EndDate, StartDate) * AgreedDailyRate * (1 - AppliedDiscountPercent / 100), 2)),
    -- supports the availability / overlap search
    INDEX idx_booking_vehicle_dates (VehicleID, StartDate, EndDate),
    INDEX idx_booking_status (BookingStatus)
) ENGINE=InnoDB;

CREATE TABLE PAYMENT (
    PaymentID       INT           NOT NULL AUTO_INCREMENT,
    BookingID       INT           NOT NULL,
    Amount          DECIMAL(10,2) NOT NULL,
    PaymentDate     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PaymentMethod   VARCHAR(30)   NOT NULL,
    PaymentStatus   VARCHAR(20)   NOT NULL DEFAULT 'Pending',
    TransactionRef  VARCHAR(100)  NULL,
    CONSTRAINT pk_payment PRIMARY KEY (PaymentID),
    CONSTRAINT uq_payment_txn UNIQUE (TransactionRef),
    CONSTRAINT fk_payment_booking FOREIGN KEY (BookingID)
        REFERENCES BOOKING (BookingID)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_payment_amount CHECK (Amount >= 0),
    CONSTRAINT chk_payment_method CHECK (PaymentMethod IN ('Cash', 'Card', 'UPI', 'Net Banking')),
    CONSTRAINT chk_payment_status CHECK (PaymentStatus IN ('Pending', 'Success', 'Failed', 'Refunded'))
) ENGINE=InnoDB;

-- [v2] Every SQL statement the Java application runs is written here, so
--      employees can inspect it on the "Query Log" screen. It is an audit
--      table: it has no foreign keys on purpose, so log rows survive even if
--      the user they mention is later removed.
CREATE TABLE QUERY_LOG (
    LogID         BIGINT        NOT NULL AUTO_INCREMENT,
    ExecutedAt    TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UserRole      VARCHAR(10)   NOT NULL,
    UserEmail     VARCHAR(150)  NULL,
    QueryType     VARCHAR(10)   NOT NULL,
    SqlText       TEXT          NOT NULL,
    RowsAffected  INT           NULL,
    DurationMs    INT           NULL,
    Status        VARCHAR(10)   NOT NULL,
    ErrorMessage  VARCHAR(500)  NULL,
    CONSTRAINT pk_query_log     PRIMARY KEY (LogID),
    CONSTRAINT chk_log_role     CHECK (UserRole IN ('CUSTOMER', 'EMPLOYEE', 'GUEST')),
    CONSTRAINT chk_log_status   CHECK (Status IN ('OK', 'ERROR')),
    INDEX idx_log_time (ExecutedAt)
) ENGINE=InnoDB;

-- =====================================================================
-- 2. BUSINESS-RULE ENFORCEMENT (procedures + triggers)
--    A CHECK constraint cannot compare one row with other rows, so the
--    rules below are enforced with triggers.
-- =====================================================================
DROP PROCEDURE IF EXISTS sp_validate_booking;
DROP PROCEDURE IF EXISTS sp_validate_pricing_rule;
DROP TRIGGER IF EXISTS trg_booking_bi;
DROP TRIGGER IF EXISTS trg_booking_bu;
DROP TRIGGER IF EXISTS trg_pricing_rule_bi;
DROP TRIGGER IF EXISTS trg_pricing_rule_bu;
DROP TRIGGER IF EXISTS trg_payment_bi;

DELIMITER $$

-- Validates a booking:
--   * non-cancelled bookings of one vehicle must not overlap  [Start, End)
--   * a NEW Pending/Confirmed booking needs an 'Available' vehicle
--   * the linked pricing rule must match the vehicle's category,
--     the rental duration and the booking start date
-- The vehicle row is locked (FOR UPDATE) so two simultaneous requests for
-- the same vehicle are processed one after the other, not in parallel.
CREATE PROCEDURE sp_validate_booking (
    IN p_booking_id INT,
    IN p_vehicle_id INT,
    IN p_start      DATE,
    IN p_end        DATE,
    IN p_status     VARCHAR(20),
    IN p_rule_id    INT,
    IN p_is_insert  TINYINT
)
BEGIN
    DECLARE v_nf          TINYINT     DEFAULT 0;
    DECLARE v_veh_cat     INT         DEFAULT NULL;
    DECLARE v_veh_status  VARCHAR(20) DEFAULT NULL;
    DECLARE v_conflicts   INT         DEFAULT 0;
    DECLARE v_rule_cat    INT         DEFAULT NULL;
    DECLARE v_min         INT         DEFAULT NULL;
    DECLARE v_max         INT         DEFAULT NULL;
    DECLARE v_from        DATE        DEFAULT NULL;
    DECLARE v_to          DATE        DEFAULT NULL;
    DECLARE v_days        INT         DEFAULT 0;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_nf = 1;

    SET v_days = DATEDIFF(p_end, p_start);
    IF v_days < 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'EndDate must be later than StartDate.';
    END IF;

    SELECT CategoryID, OperationalStatus INTO v_veh_cat, v_veh_status
      FROM VEHICLE WHERE VehicleID = p_vehicle_id FOR UPDATE;
    IF v_veh_cat IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Vehicle does not exist.';
    END IF;

    IF p_status <> 'Cancelled' THEN
        SELECT COUNT(*) INTO v_conflicts
          FROM BOOKING
         WHERE VehicleID = p_vehicle_id
           AND BookingStatus <> 'Cancelled'
           AND StartDate < p_end            -- ExistingStart < NewEnd
           AND EndDate   > p_start          -- ExistingEnd   > NewStart
           AND BookingID <> p_booking_id;
        IF v_conflicts > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Booking rejected: vehicle already booked for an overlapping period.';
        END IF;
    END IF;

    IF p_is_insert = 1 AND p_status IN ('Pending', 'Confirmed') AND v_veh_status <> 'Available' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Booking rejected: vehicle is not operational (maintenance/retired).';
    END IF;

    IF p_rule_id IS NOT NULL THEN
        SELECT CategoryID, MinDays, MaxDays, EffectiveFrom, EffectiveTo
          INTO v_rule_cat, v_min, v_max, v_from, v_to
          FROM PRICING_RULE WHERE PricingRuleID = p_rule_id;
        IF v_rule_cat IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pricing rule does not exist.';
        END IF;
        IF v_rule_cat <> v_veh_cat THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pricing rule belongs to a different vehicle category.';
        END IF;
        IF v_days < v_min OR (v_max IS NOT NULL AND v_days > v_max) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rental duration is outside the pricing rule range.';
        END IF;
        IF p_start < v_from OR (v_to IS NOT NULL AND p_start > v_to) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pricing rule is not effective on the booking start date.';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_booking_bi BEFORE INSERT ON BOOKING FOR EACH ROW
BEGIN
    CALL sp_validate_booking(COALESCE(NEW.BookingID, 0), NEW.VehicleID, NEW.StartDate, NEW.EndDate,
                             NEW.BookingStatus, NEW.PricingRuleID, 1);
END$$

CREATE TRIGGER trg_booking_bu BEFORE UPDATE ON BOOKING FOR EACH ROW
BEGIN
    CALL sp_validate_booking(OLD.BookingID, NEW.VehicleID, NEW.StartDate, NEW.EndDate,
                             NEW.BookingStatus, NEW.PricingRuleID, 0);
END$$

-- Pricing rules of one category must not be ambiguous: no two rules may
-- cover the same number of days on the same date.
CREATE PROCEDURE sp_validate_pricing_rule (
    IN p_rule_id  INT,
    IN p_category INT,
    IN p_min      INT,
    IN p_max      INT,
    IN p_from     DATE,
    IN p_to       DATE
)
BEGIN
    DECLARE v_clash INT DEFAULT 0;
    SELECT COUNT(*) INTO v_clash
      FROM PRICING_RULE r
     WHERE r.CategoryID = p_category
       AND r.PricingRuleID <> p_rule_id
       AND r.MinDays <= COALESCE(p_max, 2147483647)
       AND p_min <= COALESCE(r.MaxDays, 2147483647)
       AND r.EffectiveFrom <= COALESCE(p_to, '9999-12-31')
       AND p_from <= COALESCE(r.EffectiveTo, '9999-12-31');
    IF v_clash > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Pricing rule overlaps an existing rule for this category (days and dates).';
    END IF;
END$$

CREATE TRIGGER trg_pricing_rule_bi BEFORE INSERT ON PRICING_RULE FOR EACH ROW
BEGIN
    CALL sp_validate_pricing_rule(COALESCE(NEW.PricingRuleID, 0), NEW.CategoryID, NEW.MinDays,
                                  NEW.MaxDays, NEW.EffectiveFrom, NEW.EffectiveTo);
END$$

CREATE TRIGGER trg_pricing_rule_bu BEFORE UPDATE ON PRICING_RULE FOR EACH ROW
BEGIN
    CALL sp_validate_pricing_rule(OLD.PricingRuleID, NEW.CategoryID, NEW.MinDays,
                                  NEW.MaxDays, NEW.EffectiveFrom, NEW.EffectiveTo);
END$$

-- Successful payments for a booking may never exceed its agreed TotalAmount.
CREATE TRIGGER trg_payment_bi BEFORE INSERT ON PAYMENT FOR EACH ROW
BEGIN
    DECLARE v_total DECIMAL(10,2) DEFAULT NULL;
    DECLARE v_paid  DECIMAL(10,2) DEFAULT 0;
    IF NEW.PaymentStatus = 'Success' THEN
        SELECT TotalAmount INTO v_total FROM BOOKING WHERE BookingID = NEW.BookingID;
        SELECT COALESCE(SUM(Amount), 0) INTO v_paid
          FROM PAYMENT WHERE BookingID = NEW.BookingID AND PaymentStatus = 'Success';
        IF v_total IS NOT NULL AND v_paid + NEW.Amount > v_total THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Payment rejected: total successful payments would exceed the booking amount.';
        END IF;
    END IF;
END$$

DELIMITER ;


-- =====================================================================
-- 4. SAMPLE DATA
-- =====================================================================

-- VEHICLE_CATEGORY (3 rows)
INSERT INTO VEHICLE_CATEGORY (CategoryID, CategoryName, Description) VALUES
  (1, 'Car', 'Four-wheeler passenger cars and SUVs'),
  (2, 'Bike', 'Two-wheeler motorcycles with gears'),
  (3, 'Scooter', 'Gearless two-wheeler scooters, petrol and electric');

-- PRICING_RULE (9 rows: 3 categories x 3 duration tiers)
INSERT INTO PRICING_RULE (PricingRuleID, CategoryID, MinDays, MaxDays, DailyRate, DiscountPercent, EffectiveFrom, EffectiveTo) VALUES
  (1, 1, 1, 2, 1500.00, 0.00, '2025-01-01', NULL),
  (2, 1, 3, 6, 1300.00, 5.00, '2025-01-01', NULL),
  (3, 1, 7, NULL, 1100.00, 10.00, '2025-01-01', NULL),
  (4, 2, 1, 2, 600.00, 0.00, '2025-01-01', NULL),
  (5, 2, 3, 6, 500.00, 5.00, '2025-01-01', NULL),
  (6, 2, 7, NULL, 400.00, 10.00, '2025-01-01', NULL),
  (7, 3, 1, 2, 450.00, 0.00, '2025-01-01', NULL),
  (8, 3, 3, 6, 380.00, 5.00, '2025-01-01', NULL),
  (9, 3, 7, NULL, 300.00, 10.00, '2025-01-01', NULL);

-- VEHICLE (53 rows: 13 cars, 20 bikes, 20 scooters)  [v2: body/fuel/transmission/seats + vehicles 51-53]
INSERT INTO VEHICLE (VehicleID, RegistrationNo, Make, Model, CategoryID, OperationalStatus,
                     BodyType, FuelType, Transmission, Seats) VALUES
  (1, 'TN 23 LT 8386', 'Maruti Suzuki', 'Swift', 1, 'Available', 'Hatchback', 'Petrol', 'Manual', 5),
  (2, 'TN 73 DH 9848', 'Hyundai', 'i20', 1, 'Available', 'Hatchback', 'Petrol', 'Manual', 5),
  (3, 'TN 22 UP 9381', 'Hyundai', 'Creta', 1, 'Available', 'SUV', 'Diesel', 'Automatic', 5),
  (4, 'TN 22 SV 7227', 'Tata', 'Nexon', 1, 'Available', 'SUV', 'Petrol', 'Manual', 5),
  (5, 'TN 23 AW 1323', 'Honda', 'City', 1, 'Available', 'Sedan', 'Petrol', 'Automatic', 5),
  (6, 'TN 23 KD 7368', 'Mahindra', 'Thar', 1, 'Available', 'SUV', 'Diesel', 'Manual', 4),
  (7, 'TN 23 YS 5149', 'Kia', 'Seltos', 1, 'Available', 'SUV', 'Petrol', 'Automatic', 5),
  (8, 'TN 23 NJ 5697', 'Maruti Suzuki', 'Baleno', 1, 'Available', 'Hatchback', 'Petrol', 'Automatic', 5),
  (9, 'TN 23 NT 1251', 'Toyota', 'Innova Crysta', 1, 'Under Maintenance', 'MUV', 'Diesel', 'Manual', 7),
  (10, 'TN 73 LC 9134', 'Tata', 'Punch', 1, 'Available', 'SUV', 'Petrol', 'Manual', 5),
  (11, 'TN 22 KK 7495', 'Royal Enfield', 'Classic 350', 2, 'Available', 'Cruiser', 'Petrol', 'Manual', 2),
  (12, 'TN 23 XZ 9461', 'Royal Enfield', 'Hunter 350', 2, 'Available', 'Cruiser', 'Petrol', 'Manual', 2),
  (13, 'TN 23 AZ 6044', 'Royal Enfield', 'Meteor 350', 2, 'Available', 'Cruiser', 'Petrol', 'Manual', 2),
  (14, 'TN 23 RP 1486', 'Royal Enfield', 'Himalayan', 2, 'Available', 'Cruiser', 'Petrol', 'Manual', 2),
  (15, 'TN 21 VU 8167', 'Bajaj', 'Pulsar 150', 2, 'Available', 'Commuter', 'Petrol', 'Manual', 2),
  (16, 'TN 23 PT 9834', 'Bajaj', 'Pulsar NS200', 2, 'Available', 'Sports', 'Petrol', 'Manual', 2),
  (17, 'TN 21 NT 4238', 'Bajaj', 'Dominar 250', 2, 'Available', 'Sports', 'Petrol', 'Manual', 2),
  (18, 'TN 21 VS 8405', 'KTM', 'Duke 200', 2, 'Available', 'Sports', 'Petrol', 'Manual', 2),
  (19, 'TN 22 AV 3679', 'Yamaha', 'FZ-S Fi', 2, 'Available', 'Commuter', 'Petrol', 'Manual', 2),
  (20, 'TN 23 BZ 8573', 'Yamaha', 'R15 V4', 2, 'Available', 'Sports', 'Petrol', 'Manual', 2),
  (21, 'TN 73 DZ 7036', 'TVS', 'Apache RTR 160', 2, 'Available', 'Sports', 'Petrol', 'Manual', 2),
  (22, 'TN 21 ES 3751', 'TVS', 'Raider 125', 2, 'Under Maintenance', 'Commuter', 'Petrol', 'Manual', 2),
  (23, 'TN 23 XZ 7485', 'Hero', 'Splendor Plus', 2, 'Available', 'Commuter', 'Petrol', 'Manual', 2),
  (24, 'TN 73 SZ 8940', 'Hero', 'Xtreme 160R', 2, 'Available', 'Sports', 'Petrol', 'Manual', 2),
  (25, 'TN 23 UN 5041', 'Honda', 'Shine', 2, 'Available', 'Commuter', 'Petrol', 'Manual', 2),
  (26, 'TN 22 XT 7847', 'Honda', 'Unicorn', 2, 'Available', 'Commuter', 'Petrol', 'Manual', 2),
  (27, 'TN 21 PG 4824', 'Honda', 'CB350', 2, 'Available', 'Cruiser', 'Petrol', 'Manual', 2),
  (28, 'TN 23 AB 2456', 'Suzuki', 'Gixxer', 2, 'Available', 'Sports', 'Petrol', 'Manual', 2),
  (29, 'TN 73 WR 8568', 'Jawa', '42', 2, 'Available', 'Cruiser', 'Petrol', 'Manual', 2),
  (30, 'TN 21 MG 4262', 'Bajaj', 'Avenger Street 160', 2, 'Available', 'Cruiser', 'Petrol', 'Manual', 2),
  (31, 'TN 73 SR 8377', 'Honda', 'Activa 6G', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (32, 'TN 21 PU 0551', 'Honda', 'Activa 125', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (33, 'TN 23 LR 1612', 'Honda', 'Dio', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (34, 'TN 21 ZZ 8441', 'TVS', 'Jupiter', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (35, 'TN 23 RF 6976', 'TVS', 'Jupiter 125', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (36, 'TN 73 SC 4366', 'TVS', 'Ntorq 125', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (37, 'TN 21 MD 3189', 'Suzuki', 'Access 125', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (38, 'TN 73 PE 7363', 'Suzuki', 'Burgman Street', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (39, 'TN 23 CA 7393', 'Hero', 'Pleasure Plus', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (40, 'TN 22 UC 9997', 'Hero', 'Destini 125', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (41, 'TN 23 HD 0732', 'Hero', 'Maestro Edge 125', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (42, 'TN 22 JY 5854', 'Yamaha', 'Fascino 125', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (43, 'TN 23 AL 9553', 'Yamaha', 'RayZR 125', 3, 'Under Maintenance', 'Scooter', 'Petrol', 'Automatic', 2),
  (44, 'TN 22 SZ 2210', 'Vespa', 'SXL 125', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (45, 'TN 22 HS 9633', 'Aprilia', 'SR 125', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (46, 'TN 23 WZ 8270', 'Ather', '450X', 3, 'Available', 'Scooter', 'Electric', 'Automatic', 2),
  (47, 'TN 23 CP 2624', 'Ola', 'S1 Pro', 3, 'Available', 'Scooter', 'Electric', 'Automatic', 2),
  (48, 'TN 23 VK 8319', 'TVS', 'iQube', 3, 'Available', 'Scooter', 'Electric', 'Automatic', 2),
  (49, 'TN 21 NE 8521', 'Bajaj', 'Chetak', 3, 'Available', 'Scooter', 'Electric', 'Automatic', 2),
  (50, 'TN 23 NA 1739', 'Honda', 'Activa 6G', 3, 'Available', 'Scooter', 'Petrol', 'Automatic', 2),
  (51, 'TN 23 BV 4410', 'Hyundai', 'Verna', 1, 'Available', 'Sedan', 'Petrol', 'Automatic', 5),
  (52, 'TN 22 MD 7302', 'Maruti Suzuki', 'Dzire', 1, 'Available', 'Sedan', 'CNG', 'Manual', 5),
  (53, 'TN 73 CK 2185', 'Kia', 'Carens', 1, 'Available', 'MUV', 'Petrol', 'Manual', 7);

-- CUSTOMER (35 rows)
INSERT INTO CUSTOMER (CustomerID, FullName, Email, Phone, DrivingLicenceNo) VALUES
  (1, 'Arjun Venkatesan', 'arjun.venkatesan@yahoo.in', '+91-6055163880', 'TN4520190432689'),
  (2, 'Priya Ramanathan', 'priya.ramanathan@gmail.com', '+91-9511127328', 'TN4220177552103'),
  (3, 'Mohammed Irfan', 'mohammed.irfan@outlook.com', '+91-6534261374', 'TN6020132581856'),
  (4, 'Kavya Subramanian', 'kavya.subramanian@gmail.com', '+91-9669309519', 'TN4420223914570'),
  (5, 'Rahul Deshmukh', 'rahul.deshmukh@gmail.com', '+91-9428591642', 'MH0520142908564'),
  (6, 'Ananya Iyer', 'ananya.iyer@gmail.com', '+91-9584938506', 'KA0720223162997'),
  (7, 'Vikram Rathore', 'vikram.rathore@yahoo.in', '+91-9728678324', 'RJ0320128890861'),
  (8, 'Sneha Kulkarni', 'sneha.kulkarni@gmail.com', '+91-9960586281', 'MH1820133120650'),
  (9, 'Karthik Raghavan', 'karthik.raghavan@yahoo.in', '+91-8456720675', 'TN6920211319676'),
  (10, 'Divya Lakshmi', 'divya.lakshmi@yahoo.in', '+91-8201003165', 'TN4520134419852'),
  (11, 'Aditya Mehra', 'aditya.mehra@yahoo.in', '+91-9837784964', 'DL5420177846711'),
  (12, 'Nandini Krishnan', 'nandini.krishnan@gmail.com', '+91-8557631828', 'TN6220222015038'),
  (13, 'Faisal Ahmed', 'faisal.ahmed@outlook.com', '+91-6910393422', 'KA2820150082307'),
  (14, 'Pooja Nair', 'pooja.nair@gmail.com', '+91-9693292768', 'KL4320219144592'),
  (15, 'Siddharth Menon', 'siddharth.menon@gmail.com', '+91-6377903604', 'KL2120199428839'),
  (16, 'Meenakshi Sundaram', 'meenakshi.sundaram@gmail.com', '+91-6666562195', 'TN3620149560246'),
  (17, 'Rohan Gupta', 'rohan.gupta@outlook.com', '+91-9287870937', 'DL7220121702145'),
  (18, 'Ishita Banerjee', 'ishita.banerjee@outlook.com', '+91-6468208209', 'WB2220111013782'),
  (19, 'Harish Kumar', 'harish.kumar@outlook.com', '+91-9861481428', 'TN2420118499786'),
  (20, 'Lavanya Murugan', 'lavanya.murugan@outlook.com', '+91-8631476759', 'TN0920175278143'),
  (21, 'Abhishek Tiwari', 'abhishek.tiwari@gmail.com', '+91-8275011139', 'UP0920086906213'),
  (22, 'Shruti Patil', 'shruti.patil@outlook.com', '+91-9016567828', 'MH0820113946428'),
  (23, 'Naveen Prasad', 'naveen.prasad@gmail.com', '+91-8132092060', 'AP1320195284395'),
  (24, 'Tanvi Joshi', 'tanvi.joshi@yahoo.in', '+91-9671869953', 'MH7920096781193'),
  (25, 'Gautham Selvam', 'gautham.selvam@outlook.com', '+91-9231537636', 'TN7720227149174'),
  (26, 'Aarti Sharma', 'aarti.sharma@gmail.com', '+91-9401737149', 'GJ6520166527131'),
  (27, 'Imran Sheikh', 'imran.sheikh@outlook.com', '+91-8061880504', 'TS5620084721628'),
  (28, 'Deepika Chandran', 'deepika.chandran@outlook.com', '+91-8780746286', 'TN1620176990001'),
  (29, 'Manoj Pillai', 'manoj.pillai@outlook.com', '+91-9969546438', 'KL0320183312633'),
  (30, 'Revathi Annamalai', 'revathi.annamalai@gmail.com', '+91-9120900758', 'TN2120111984459'),
  (31, 'Sanjay Reddy', 'sanjay.reddy@gmail.com', '+91-7561120183', 'TS4620170483087'),
  (32, 'Zoya Khan', 'zoya.khan@yahoo.in', '+91-9135611246', 'UP4620216444228'),
  (33, 'Praveen Raj', 'praveen.raj@yahoo.in', '+91-7833867270', 'TN5420152100643'),
  (34, 'Harini Balaji', 'harini.balaji@gmail.com', '+91-9030512653', 'TN7020204277760'),
  (35, 'Dhruv Malhotra', 'dhruv.malhotra@gmail.com', '+91-7025084757', 'DL0320169078991');

-- [v2] demo password "Password@123" for every sample customer.
--      Each customer gets their own 16-character salt; the hash is
--      SHA-256(salt + password), exactly what the Java app computes.
--      (New sign-ups get a random salt from the app.)
UPDATE CUSTOMER
   SET PasswordSalt = LEFT(SHA2(CONCAT('vrs-demo-salt-', CustomerID), 256), 16),
       PasswordHash = SHA2(CONCAT(LEFT(SHA2(CONCAT('vrs-demo-salt-', CustomerID), 256), 16), 'Password@123'), 256)
 WHERE CustomerID BETWEEN 1 AND 35;

-- [v2] EMPLOYEE (2 rows)  -  admin@vrs.in / Admin@123,  desk@vrs.in / Staff@123
INSERT INTO EMPLOYEE (EmployeeID, FullName, Email, Phone, Designation, PasswordSalt, PasswordHash) VALUES
  (1, 'Admin', 'admin@vrs.in', '+91-9000000001', 'Manager',
      '5f2c9a71d04be3a8', SHA2(CONCAT('5f2c9a71d04be3a8', 'Admin@123'), 256)),
  (2, 'Front Desk', 'desk@vrs.in', '+91-9000000002', 'Rental Operator',
      'c81e4b07a95d2f36', SHA2(CONCAT('c81e4b07a95d2f36', 'Staff@123'), 256));

-- BOOKING (90 rows) - every row passes the overlap / pricing triggers
-- [v2] HandledBy: staff member who confirmed it (NULL = still pending / cancelled by the customer)
INSERT INTO BOOKING (BookingID, CustomerID, VehicleID, PricingRuleID, StartDate, EndDate, BookingStatus, AgreedDailyRate, AppliedDiscountPercent, TotalAmount, CreatedAt, HandledBy) VALUES
  (1, 14, 23, 6, '2026-08-06', '2026-08-17', 'Completed', 400.00, 10.00, 3960.00, '2026-07-16 20:36:29', 2),
  (2, 24, 36, 9, '2026-08-04', '2026-08-13', 'Completed', 300.00, 10.00, 2430.00, '2026-07-22 11:55:44', 2),
  (3, 13, 34, 8, '2026-08-09', '2026-08-14', 'Completed', 380.00, 5.00, 1805.00, '2026-07-31 08:28:38', 1),
  (4, 29, 3, 2, '2026-08-14', '2026-08-19', 'Completed', 1300.00, 5.00, 6175.00, '2026-08-04 08:47:37', 2),
  (5, 31, 50, 8, '2026-08-22', '2026-08-27', 'Completed', 380.00, 5.00, 1805.00, '2026-08-13 11:45:59', 2),
  (6, 4, 3, 2, '2026-09-03', '2026-09-06', 'Completed', 1300.00, 5.00, 3705.00, '2026-08-13 15:27:22', 1),
  (7, 4, 47, 8, '2026-08-31', '2026-09-03', 'Completed', 380.00, 5.00, 1083.00, '2026-08-17 18:04:16', 2),
  (8, 9, 21, 5, '2026-08-29', '2026-09-01', 'Completed', 500.00, 5.00, 1425.00, '2026-08-19 08:05:37', 2),
  (9, 10, 34, 7, '2026-08-24', '2026-08-25', 'Completed', 450.00, 0.00, 450.00, '2026-08-20 09:14:53', 1),
  (10, 10, 3, 1, '2026-09-01', '2026-09-03', 'Completed', 1500.00, 0.00, 3000.00, '2026-08-22 13:08:24', 2),
  (11, 20, 33, 8, '2026-09-11', '2026-09-15', 'Completed', 380.00, 5.00, 1444.00, '2026-08-25 18:30:07', 2),
  (12, 13, 27, 5, '2026-09-09', '2026-09-12', 'Completed', 500.00, 5.00, 1425.00, '2026-08-26 14:00:15', 1),
  (13, 21, 13, 6, '2026-09-16', '2026-09-24', 'Completed', 400.00, 10.00, 2880.00, '2026-08-30 16:55:24', 2),
  (14, 14, 27, 4, '2026-09-12', '2026-09-13', 'Completed', 600.00, 0.00, 600.00, '2026-08-31 09:16:10', 2),
  (15, 16, 35, 9, '2026-09-16', '2026-09-25', 'Completed', 300.00, 10.00, 2430.00, '2026-09-01 19:53:19', 1),
  (16, 19, 5, 1, '2026-09-17', '2026-09-18', 'Completed', 1500.00, 0.00, 1500.00, '2026-09-02 17:22:05', 2),
  (17, 4, 44, 7, '2026-09-17', '2026-09-18', 'Completed', 450.00, 0.00, 450.00, '2026-09-04 09:29:38', 2),
  (18, 28, 33, 9, '2026-09-10', '2026-09-19', 'Cancelled', 300.00, 10.00, 2430.00, '2026-09-04 19:29:37', NULL),
  (19, 6, 5, 3, '2026-09-18', '2026-09-28', 'Completed', 1100.00, 10.00, 9900.00, '2026-09-09 20:42:14', 2),
  (20, 33, 21, 4, '2026-09-28', '2026-09-29', 'Cancelled', 600.00, 0.00, 600.00, '2026-09-10 13:48:16', NULL),
  (21, 26, 5, 2, '2026-09-28', '2026-10-03', 'Completed', 1300.00, 5.00, 6175.00, '2026-09-12 14:20:11', 1),
  (22, 4, 50, 7, '2026-09-25', '2026-09-27', 'Cancelled', 450.00, 0.00, 900.00, '2026-09-15 09:55:38', NULL),
  (23, 9, 25, 5, '2026-09-21', '2026-09-27', 'Completed', 500.00, 5.00, 2850.00, '2026-09-15 15:12:49', 2),
  (24, 28, 23, 4, '2026-09-21', '2026-09-23', 'Completed', 600.00, 0.00, 1200.00, '2026-09-17 14:05:10', 1),
  (25, 14, 14, 6, '2026-10-05', '2026-10-17', 'Completed', 400.00, 10.00, 4320.00, '2026-09-17 18:53:54', 2),
  (26, 23, 45, 8, '2026-09-21', '2026-09-26', 'Completed', 380.00, 5.00, 1805.00, '2026-09-20 09:56:04', 2),
  (27, 21, 24, 6, '2026-10-10', '2026-10-22', 'Cancelled', 400.00, 10.00, 4320.00, '2026-09-20 16:07:46', NULL),
  (28, 22, 5, 2, '2026-10-03', '2026-10-08', 'Completed', 1300.00, 5.00, 6175.00, '2026-09-21 14:53:51', 2),
  (29, 24, 13, 4, '2026-10-08', '2026-10-09', 'Completed', 600.00, 0.00, 600.00, '2026-09-21 21:33:08', 2),
  (30, 28, 29, 6, '2026-10-08', '2026-10-16', 'Completed', 400.00, 10.00, 2880.00, '2026-09-22 08:18:05', 1),
  (31, 24, 3, 1, '2026-10-11', '2026-10-12', 'Completed', 1500.00, 0.00, 1500.00, '2026-09-23 10:10:01', 2),
  (32, 3, 1, 1, '2026-10-04', '2026-10-05', 'Completed', 1500.00, 0.00, 1500.00, '2026-09-26 15:03:14', 2),
  (33, 5, 25, 5, '2026-09-27', '2026-10-03', 'Completed', 500.00, 5.00, 2850.00, '2026-09-26 15:13:24', 1),
  (34, 3, 29, 4, '2026-10-16', '2026-10-17', 'Completed', 600.00, 0.00, 600.00, '2026-09-26 18:02:30', 2),
  (35, 23, 47, 7, '2026-10-07', '2026-10-08', 'Completed', 450.00, 0.00, 450.00, '2026-10-01 08:54:09', 2),
  (36, 28, 42, 8, '2026-10-02', '2026-10-08', 'Completed', 380.00, 5.00, 2166.00, '2026-10-01 09:15:13', 1),
  (37, 12, 42, 8, '2026-10-08', '2026-10-14', 'Cancelled', 380.00, 5.00, 2166.00, '2026-10-02 14:32:12', NULL),
  (38, 26, 45, 8, '2026-10-06', '2026-10-12', 'Completed', 380.00, 5.00, 2166.00, '2026-10-02 21:52:12', 2),
  (39, 11, 24, 4, '2026-10-18', '2026-10-20', 'Completed', 600.00, 0.00, 1200.00, '2026-10-03 19:15:25', 1),
  (40, 8, 1, 3, '2026-10-15', '2026-10-25', 'Confirmed', 1100.00, 10.00, 9900.00, '2026-10-03 20:14:35', 2),
  (41, 24, 40, 8, '2026-10-23', '2026-10-28', 'Confirmed', 380.00, 5.00, 1805.00, '2026-10-05 12:53:26', 2),
  (42, 11, 17, 5, '2026-10-09', '2026-10-15', 'Completed', 500.00, 5.00, 2850.00, '2026-10-05 13:37:54', 1),
  (43, 31, 41, 7, '2026-10-18', '2026-10-20', 'Completed', 450.00, 0.00, 900.00, '2026-10-05 17:57:58', 2),
  (44, 31, 40, 8, '2026-10-28', '2026-11-02', 'Pending', 380.00, 5.00, 1805.00, '2026-10-08 16:22:53', NULL),
  (45, 15, 37, 7, '2026-11-01', '2026-11-02', 'Confirmed', 450.00, 0.00, 450.00, '2026-10-11 16:25:13', 1),
  (46, 10, 39, 7, '2026-10-18', '2026-10-20', 'Completed', 450.00, 0.00, 900.00, '2026-10-13 15:24:40', 2),
  (47, 4, 6, 2, '2026-10-25', '2026-10-29', 'Confirmed', 1300.00, 5.00, 4940.00, '2026-10-14 21:07:20', 2),
  (48, 21, 35, 8, '2026-10-22', '2026-10-27', 'Confirmed', 380.00, 5.00, 1805.00, '2026-10-16 09:39:35', 1),
  (49, 21, 26, 5, '2026-11-02', '2026-11-06', 'Confirmed', 500.00, 5.00, 1900.00, '2026-10-16 13:17:49', 2),
  (50, 27, 32, 7, '2026-12-08', '2026-12-09', 'Cancelled', 450.00, 0.00, 450.00, '2026-10-18 07:49:00', NULL),
  (51, 6, 30, 4, '2027-01-01', '2027-01-02', 'Confirmed', 600.00, 0.00, 600.00, '2026-10-18 15:35:00', 1),
  (52, 17, 12, 5, '2026-11-16', '2026-11-22', 'Cancelled', 500.00, 5.00, 2850.00, '2026-10-18 16:11:00', NULL),
  (53, 28, 4, 2, '2026-11-22', '2026-11-25', 'Pending', 1300.00, 5.00, 3705.00, '2026-10-18 16:51:00', NULL),
  (54, 1, 2, 1, '2026-11-29', '2026-12-01', 'Confirmed', 1500.00, 0.00, 3000.00, '2026-10-18 19:16:00', 1),
  (55, 9, 10, 1, '2026-11-22', '2026-11-23', 'Pending', 1500.00, 0.00, 1500.00, '2026-10-18 19:17:00', NULL),
  (56, 8, 11, 6, '2026-11-17', '2026-11-26', 'Pending', 400.00, 10.00, 3240.00, '2026-10-18 21:07:00', NULL),
  (57, 18, 10, 1, '2026-12-02', '2026-12-04', 'Cancelled', 1500.00, 0.00, 3000.00, '2026-10-18 21:46:00', NULL),
  (58, 8, 6, 1, '2026-12-25', '2026-12-27', 'Confirmed', 1500.00, 0.00, 3000.00, '2026-10-19 02:02:00', 2),
  (59, 5, 16, 5, '2026-12-01', '2026-12-05', 'Pending', 500.00, 5.00, 1900.00, '2026-10-19 03:02:00', NULL),
  (60, 8, 11, 4, '2026-11-15', '2026-11-17', 'Confirmed', 600.00, 0.00, 1200.00, '2026-10-19 03:52:00', 1),
  (61, 31, 1, 3, '2026-11-16', '2026-11-25', 'Confirmed', 1100.00, 10.00, 8910.00, '2026-10-19 07:03:00', 2),
  (62, 28, 46, 7, '2026-12-22', '2026-12-23', 'Confirmed', 450.00, 0.00, 450.00, '2026-10-19 09:06:00', 2),
  (63, 20, 28, 5, '2026-12-12', '2026-12-15', 'Cancelled', 500.00, 5.00, 1425.00, '2026-10-19 09:07:00', NULL),
  (64, 21, 46, 8, '2027-01-20', '2027-01-24', 'Confirmed', 380.00, 5.00, 1444.00, '2026-10-19 15:29:00', 2),
  (65, 21, 18, 4, '2026-11-16', '2026-11-17', 'Confirmed', 600.00, 0.00, 600.00, '2026-10-19 20:18:00', 2),
  (66, 27, 18, 4, '2026-11-28', '2026-11-30', 'Confirmed', 600.00, 0.00, 1200.00, '2026-10-19 20:31:00', 1),
  (67, 4, 49, 7, '2026-12-08', '2026-12-10', 'Confirmed', 450.00, 0.00, 900.00, '2026-10-19 21:44:00', 2),
  (68, 14, 17, 4, '2026-10-29', '2026-10-31', 'Cancelled', 600.00, 0.00, 1200.00, '2026-10-20 00:51:00', NULL),
  (69, 11, 31, 8, '2026-12-03', '2026-12-08', 'Confirmed', 380.00, 5.00, 1805.00, '2026-10-20 07:55:00', 1),
  (70, 7, 20, 4, '2026-11-19', '2026-11-20', 'Confirmed', 600.00, 0.00, 600.00, '2026-10-20 11:35:00', 2),
  (71, 8, 7, 1, '2026-12-23', '2026-12-25', 'Pending', 1500.00, 0.00, 3000.00, '2026-10-20 19:00:00', NULL),
  (72, 10, 4, 1, '2026-12-11', '2026-12-13', 'Confirmed', 1500.00, 0.00, 3000.00, '2026-10-21 03:19:00', 1),
  (73, 18, 10, 1, '2026-10-22', '2026-10-23', 'Completed', 1500.00, 0.00, 1500.00, '2026-10-21 09:09:21', 2),
  (74, 10, 16, 6, '2026-11-15', '2026-11-22', 'Confirmed', 400.00, 10.00, 2520.00, '2026-10-21 12:26:00', 2),
  (75, 31, 6, 1, '2026-11-27', '2026-11-29', 'Confirmed', 1500.00, 0.00, 3000.00, '2026-10-21 12:42:00', 1),
  (76, 11, 31, 7, '2026-11-21', '2026-11-23', 'Confirmed', 450.00, 0.00, 900.00, '2026-10-21 18:08:00', 2),
  (77, 15, 38, 8, '2026-11-22', '2026-11-28', 'Pending', 380.00, 5.00, 2166.00, '2026-10-21 18:49:00', NULL),
  (78, 25, 26, 4, '2026-11-08', '2026-11-09', 'Cancelled', 600.00, 0.00, 600.00, '2026-10-22 01:23:00', NULL),
  (79, 9, 19, 6, '2026-12-02', '2026-12-14', 'Pending', 400.00, 10.00, 4320.00, '2026-10-22 02:13:00', NULL),
  (80, 10, 7, 1, '2026-12-25', '2026-12-26', 'Confirmed', 1500.00, 0.00, 1500.00, '2026-10-22 03:10:00', 2),
  (81, 10, 30, 4, '2027-01-18', '2027-01-20', 'Confirmed', 600.00, 0.00, 1200.00, '2026-10-22 11:50:00', 1),
  (82, 26, 19, 5, '2026-12-14', '2026-12-19', 'Pending', 500.00, 5.00, 2375.00, '2026-10-22 12:47:00', NULL),
  (83, 19, 7, 2, '2026-11-30', '2026-12-06', 'Confirmed', 1300.00, 5.00, 7410.00, '2026-10-22 13:00:00', 2),
  (84, 20, 2, 1, '2026-12-07', '2026-12-09', 'Confirmed', 1500.00, 0.00, 3000.00, '2026-10-22 16:28:00', 1),
  (85, 26, 20, 5, '2026-11-20', '2026-11-25', 'Pending', 500.00, 5.00, 2375.00, '2026-10-22 20:25:00', NULL),
  (86, 6, 14, 5, '2026-11-12', '2026-11-16', 'Confirmed', 500.00, 5.00, 1900.00, '2026-10-22 20:52:00', 2),
  (87, 14, 2, 2, '2026-12-04', '2026-12-07', 'Confirmed', 1300.00, 5.00, 3705.00, '2026-10-23 07:03:00', 1),
  (88, 7, 48, 7, '2026-11-06', '2026-11-08', 'Pending', 450.00, 0.00, 900.00, '2026-10-23 13:34:00', NULL),
  (89, 1, 12, 6, '2026-11-08', '2026-11-19', 'Confirmed', 400.00, 10.00, 3960.00, '2026-10-23 13:42:21', 2),
  (90, 13, 32, 8, '2026-11-25', '2026-11-30', 'Confirmed', 380.00, 5.00, 1805.00, '2026-10-24 03:19:00', 1);

-- PAYMENT (101 rows)
INSERT INTO PAYMENT (PaymentID, BookingID, Amount, PaymentDate, PaymentMethod, PaymentStatus, TransactionRef) VALUES
  (1, 1, 3960.00, '2026-07-16 22:12:29', 'UPI', 'Success', 'UPI571929480853'),
  (2, 2, 970.00, '2026-07-22 17:25:44', 'UPI', 'Success', 'UPI424761341461'),
  (3, 3, 1805.00, '2026-07-31 11:16:38', 'Card', 'Success', 'CRD147865893059'),
  (4, 2, 1460.00, '2026-08-04 11:20:00', 'UPI', 'Success', 'UPI201461338005'),
  (5, 4, 3080.00, '2026-08-04 13:02:37', 'Net Banking', 'Success', 'NBK605538488568'),
  (6, 5, 1805.00, '2026-08-13 11:57:59', 'Card', 'Failed', 'CRD363343701087'),
  (7, 5, 1805.00, '2026-08-13 12:13:59', 'UPI', 'Success', 'UPI623942464668'),
  (8, 6, 3705.00, '2026-08-13 18:44:22', 'UPI', 'Success', 'UPI539899745358'),
  (9, 4, 3095.00, '2026-08-14 08:40:00', 'Card', 'Success', 'CRD067090727615'),
  (10, 7, 1083.00, '2026-08-17 20:26:16', 'UPI', 'Success', 'UPI217233496770'),
  (11, 8, 1425.00, '2026-08-19 09:02:37', 'Net Banking', 'Failed', 'NBK410475168865'),
  (12, 8, 1425.00, '2026-08-19 09:20:37', 'UPI', 'Success', 'UPI141082359534'),
  (13, 9, 450.00, '2026-08-20 15:06:53', 'UPI', 'Success', 'UPI429474781824'),
  (14, 10, 1200.00, '2026-08-22 13:17:24', 'UPI', 'Success', 'UPI619568080100'),
  (15, 11, 1444.00, '2026-08-25 20:27:07', 'UPI', 'Success', 'UPI057030488778'),
  (16, 12, 420.00, '2026-08-26 17:40:15', 'Net Banking', 'Success', 'NBK611463511020'),
  (17, 13, 2880.00, '2026-08-30 17:31:24', 'Card', 'Failed', 'CRD155293012670'),
  (18, 13, 2880.00, '2026-08-30 17:52:24', 'Net Banking', 'Success', 'NBK129770579299'),
  (19, 14, 600.00, '2026-08-31 13:41:10', 'Card', 'Success', 'CRD133702718615'),
  (20, 10, 1800.00, '2026-09-01 08:40:00', 'UPI', 'Success', 'UPI200700917039'),
  (21, 15, 2430.00, '2026-09-02 00:56:19', 'UPI', 'Success', 'UPI991470675548'),
  (22, 16, 440.00, '2026-09-02 21:03:05', 'Card', 'Success', 'CRD351676825973'),
  (23, 17, 450.00, '2026-09-04 15:24:38', 'UPI', 'Success', 'UPI264366282909'),
  (24, 18, 720.00, '2026-09-05 00:04:37', 'UPI', 'Success', 'UPI242204262837'),
  (25, 18, 720.00, '2026-09-07 23:19:37', 'UPI', 'Refunded', 'RFDUPI095060188987'),
  (26, 12, 1005.00, '2026-09-09 09:32:00', 'UPI', 'Success', 'UPI004030042637'),
  (27, 19, 9900.00, '2026-09-09 21:48:14', 'UPI', 'Success', 'UPI780133252695'),
  (28, 20, 600.00, '2026-09-10 14:20:16', 'Net Banking', 'Failed', 'NBK378276082196'),
  (29, 21, 1850.00, '2026-09-12 15:13:11', 'UPI', 'Success', 'UPI856070022386'),
  (30, 22, 900.00, '2026-09-15 10:47:38', 'Card', 'Failed', 'CRD100637171124'),
  (31, 23, 2850.00, '2026-09-15 15:54:49', 'Card', 'Success', 'CRD658509868015'),
  (32, 16, 1060.00, '2026-09-17 10:35:00', 'Cash', 'Success', NULL),
  (33, 24, 1200.00, '2026-09-17 14:49:10', 'UPI', 'Success', 'UPI030225021520'),
  (34, 26, 1805.00, '2026-09-20 15:34:04', 'UPI', 'Success', 'UPI418341908238'),
  (35, 28, 1850.00, '2026-09-21 18:09:51', 'UPI', 'Success', 'UPI122238637877'),
  (36, 29, 240.00, '2026-09-21 23:55:08', 'UPI', 'Success', 'UPI297447660471'),
  (37, 30, 2880.00, '2026-09-22 10:15:05', 'UPI', 'Success', 'UPI338792687710'),
  (38, 31, 750.00, '2026-09-23 13:40:01', 'Card', 'Success', 'CRD446940372056'),
  (39, 32, 1500.00, '2026-09-26 15:37:14', 'UPI', 'Success', 'UPI788203962209'),
  (40, 33, 1140.00, '2026-09-26 18:47:24', 'Net Banking', 'Success', 'NBK539711009897'),
  (41, 34, 600.00, '2026-09-26 22:22:30', 'UPI', 'Success', 'UPI820624431774'),
  (42, 33, 1710.00, '2026-09-27 11:42:00', 'UPI', 'Success', 'UPI282207016929'),
  (43, 21, 4325.00, '2026-09-28 11:38:00', 'Net Banking', 'Success', 'NBK343274655013'),
  (44, 36, 2166.00, '2026-10-01 13:01:13', 'UPI', 'Success', 'UPI777921415344'),
  (45, 38, 1080.00, '2026-10-03 01:18:12', 'Card', 'Success', 'CRD252189903321'),
  (46, 28, 4325.00, '2026-10-03 11:02:00', 'UPI', 'Success', 'UPI054246854242'),
  (47, 40, 9900.00, '2026-10-03 23:03:35', 'UPI', 'Success', 'UPI798371763452'),
  (48, 39, 1200.00, '2026-10-03 23:30:25', 'Card', 'Success', 'CRD150295292074'),
  (49, 25, 4320.00, '2026-10-05 08:17:00', 'Cash', 'Success', NULL),
  (50, 42, 1140.00, '2026-10-05 13:43:54', 'UPI', 'Success', 'UPI145946096584'),
  (51, 43, 900.00, '2026-10-05 20:19:58', 'Card', 'Success', 'CRD851481013315'),
  (52, 38, 1086.00, '2026-10-06 09:46:00', 'UPI', 'Success', 'UPI686922713831'),
  (53, 35, 450.00, '2026-10-07 10:13:00', 'Cash', 'Success', NULL),
  (54, 29, 360.00, '2026-10-08 09:39:00', 'UPI', 'Success', 'UPI055049657096'),
  (55, 42, 1710.00, '2026-10-09 10:47:00', 'Cash', 'Success', NULL),
  (56, 31, 750.00, '2026-10-11 09:52:00', 'Net Banking', 'Success', 'NBK266075689651'),
  (57, 45, 220.00, '2026-10-11 16:44:13', 'Net Banking', 'Success', 'NBK378195961010'),
  (58, 47, 1970.00, '2026-10-14 23:26:20', 'Card', 'Success', 'CRD615157539210'),
  (59, 48, 1805.00, '2026-10-16 10:22:35', 'UPI', 'Success', 'UPI423733697951'),
  (60, 49, 1900.00, '2026-10-16 15:49:49', 'UPI', 'Success', 'UPI247448059498'),
  (61, 47, 2970.00, '2026-10-17 07:04:20', 'UPI', 'Success', 'UPI549092188904'),
  (62, 46, 900.00, '2026-10-18 10:45:00', 'Cash', 'Success', NULL),
  (63, 53, 1480.00, '2026-10-18 17:01:00', 'UPI', 'Failed', 'UPI575069426698'),
  (64, 51, 240.00, '2026-10-18 20:07:00', 'Card', 'Success', 'CRD625284561863'),
  (65, 54, 3000.00, '2026-10-19 00:57:00', 'Card', 'Success', 'CRD331791430747'),
  (66, 57, 1500.00, '2026-10-19 03:20:00', 'Net Banking', 'Success', 'NBK691804418613'),
  (67, 58, 1500.00, '2026-10-19 05:33:00', 'UPI', 'Success', 'UPI807567817246'),
  (68, 60, 480.00, '2026-10-19 09:17:00', 'Card', 'Success', 'CRD978176887849'),
  (69, 62, 450.00, '2026-10-19 11:40:00', 'UPI', 'Success', 'UPI836708878531'),
  (70, 61, 8910.00, '2026-10-19 12:49:00', 'Net Banking', 'Success', 'NBK179785575660'),
  (71, 64, 570.00, '2026-10-19 19:59:00', 'Card', 'Success', 'CRD177483640320'),
  (72, 65, 170.00, '2026-10-20 00:09:00', 'UPI', 'Success', 'UPI290357505807'),
  (73, 67, 260.00, '2026-10-20 00:35:00', 'Net Banking', 'Success', 'NBK378736674714'),
  (74, 66, 480.00, '2026-10-20 00:55:00', 'Card', 'Success', 'CRD457910169929'),
  (75, 69, 1805.00, '2026-10-20 08:06:00', 'UPI', 'Success', 'UPI430964282431'),
  (76, 70, 170.00, '2026-10-20 13:20:00', 'Card', 'Success', 'CRD918381495744'),
  (77, 58, 1500.00, '2026-10-20 18:49:00', 'Card', 'Success', 'CRD122028485258'),
  (78, 66, 720.00, '2026-10-21 01:30:00', 'Net Banking', 'Success', 'NBK832717451435'),
  (79, 70, 430.00, '2026-10-21 04:20:00', 'Card', 'Success', 'CRD866271252042'),
  (80, 72, 890.00, '2026-10-21 09:11:00', 'Net Banking', 'Success', 'NBK626958359027'),
  (81, 74, 1260.00, '2026-10-21 12:49:00', 'Card', 'Success', 'CRD146555722348'),
  (82, 75, 3000.00, '2026-10-21 13:11:00', 'Net Banking', 'Success', 'NBK226549089823'),
  (83, 67, 640.00, '2026-10-21 18:29:00', 'UPI', 'Success', 'UPI231245287713'),
  (84, 77, 860.00, '2026-10-21 19:07:00', 'UPI', 'Pending', 'UPI447445199559'),
  (85, 76, 360.00, '2026-10-21 19:59:00', 'UPI', 'Success', 'UPI590619227553'),
  (86, 57, 1500.00, '2026-10-21 23:47:00', 'Net Banking', 'Refunded', 'RFDNBK406956314395'),
  (87, 78, 300.00, '2026-10-22 02:52:00', 'Net Banking', 'Success', 'NBK981703613873'),
  (88, 80, 1500.00, '2026-10-22 08:45:00', 'Net Banking', 'Success', 'NBK837612539825'),
  (89, 73, 1500.00, '2026-10-22 09:03:00', 'Cash', 'Success', NULL),
  (90, 83, 2960.00, '2026-10-22 15:27:00', 'Card', 'Success', 'CRD274368043104'),
  (91, 81, 1200.00, '2026-10-22 15:47:00', 'Card', 'Success', 'CRD073080908627'),
  (92, 84, 3000.00, '2026-10-22 17:53:00', 'UPI', 'Success', 'UPI614225152165'),
  (93, 86, 1900.00, '2026-10-23 02:35:00', 'Cash', 'Success', NULL),
  (94, 78, 300.00, '2026-10-23 03:37:00', 'Net Banking', 'Refunded', 'RFDNBK375268057860'),
  (95, 87, 1110.00, '2026-10-23 10:36:00', 'UPI', 'Success', 'UPI581885893832'),
  (96, 41, 1805.00, '2026-10-23 11:57:00', 'Cash', 'Success', NULL),
  (97, 88, 360.00, '2026-10-23 13:36:00', 'UPI', 'Pending', 'UPI696764479783'),
  (98, 89, 3960.00, '2026-10-23 19:27:21', 'Card', 'Success', 'CRD177603423659'),
  (99, 72, 2110.00, '2026-10-24 02:26:00', 'UPI', 'Success', 'UPI052865946055'),
  (100, 90, 1805.00, '2026-10-24 03:46:00', 'Cash', 'Success', NULL),
  (101, 87, 2595.00, '2026-10-24 11:30:00', 'Net Banking', 'Success', 'NBK077091317570');

-- =====================================================================
-- 5. QUICK VERIFICATION (row counts)
-- =====================================================================
SELECT 'VEHICLE_CATEGORY' AS table_name, COUNT(*) AS row_count FROM VEHICLE_CATEGORY
UNION ALL SELECT 'PRICING_RULE', COUNT(*) FROM PRICING_RULE
UNION ALL SELECT 'VEHICLE',      COUNT(*) FROM VEHICLE
UNION ALL SELECT 'CUSTOMER',     COUNT(*) FROM CUSTOMER
UNION ALL SELECT 'BOOKING',      COUNT(*) FROM BOOKING
UNION ALL SELECT 'PAYMENT',      COUNT(*) FROM PAYMENT
UNION ALL SELECT 'EMPLOYEE',     COUNT(*) FROM EMPLOYEE
UNION ALL SELECT 'QUERY_LOG',    COUNT(*) FROM QUERY_LOG;

-- =====================================================================
-- 6. EXAMPLE QUERIES / TESTS (uncomment to try; not executed on import)
-- =====================================================================

-- (a) Vehicles available for a requested period [2026-12-01, 2026-12-05)
-- SELECT v.VehicleID, v.RegistrationNo, v.Make, v.Model, c.CategoryName
--   FROM VEHICLE v
--   JOIN VEHICLE_CATEGORY c ON c.CategoryID = v.CategoryID
--  WHERE v.OperationalStatus = 'Available'
--    AND NOT EXISTS (
--          SELECT 1 FROM BOOKING b
--           WHERE b.VehicleID = v.VehicleID
--             AND b.BookingStatus <> 'Cancelled'
--             AND b.StartDate < '2026-12-05'
--             AND b.EndDate   > '2026-12-01');

-- (b) Pricing rule that applies to a 4-day Bike rental starting 2026-12-01
-- SELECT * FROM PRICING_RULE
--  WHERE CategoryID = 2 AND 4 >= MinDays AND (MaxDays IS NULL OR 4 <= MaxDays)
--    AND '2026-12-01' >= EffectiveFrom AND (EffectiveTo IS NULL OR '2026-12-01' <= EffectiveTo);

-- (c) Outstanding balance per booking (Confirmed / Completed)
-- SELECT b.BookingID, b.TotalAmount,
--        COALESCE(SUM(CASE WHEN p.PaymentStatus = 'Success' THEN p.Amount END), 0) AS PaidSoFar,
--        b.TotalAmount - COALESCE(SUM(CASE WHEN p.PaymentStatus = 'Success' THEN p.Amount END), 0) AS Balance
--   FROM BOOKING b LEFT JOIN PAYMENT p ON p.BookingID = b.BookingID
--  WHERE b.BookingStatus IN ('Confirmed', 'Completed')
--  GROUP BY b.BookingID, b.TotalAmount;

-- (d) Revenue by vehicle category
-- SELECT c.CategoryName, COUNT(DISTINCT b.BookingID) AS bookings, SUM(p.Amount) AS collected
--   FROM PAYMENT p
--   JOIN BOOKING b ON b.BookingID = p.BookingID
--   JOIN VEHICLE v ON v.VehicleID = b.VehicleID
--   JOIN VEHICLE_CATEGORY c ON c.CategoryID = v.CategoryID
--  WHERE p.PaymentStatus = 'Success'
--  GROUP BY c.CategoryName;

-- (e) TEST: this request overlaps an existing active booking and must be REJECTED by the trigger
--     (expected: ERROR 1644 "Booking rejected: vehicle already booked for an overlapping period.")
-- INSERT INTO BOOKING (CustomerID, VehicleID, PricingRuleID, StartDate, EndDate, BookingStatus,
--                      AgreedDailyRate, AppliedDiscountPercent, TotalAmount)
-- VALUES (1, 6, 1, '2026-10-25', '2026-10-27', 'Confirmed', 1500.00, 0.00, 3000.00);

-- (f) TEST: a back-to-back booking (starts on the day the other ends) is ACCEPTED under [Start, End)
--     Run (e) with StartDate = the existing booking's EndDate to see it succeed.
