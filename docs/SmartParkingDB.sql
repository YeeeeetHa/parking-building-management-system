-- ===========================================================================
-- NOTICE
-- This database may be outdated as later sprint arrives
-- Please refer to the project database creators for more detail
-- ===========================================================================

-- ===========================================================================
-- DATABASE INITIALIZATION
-- ===========================================================================
IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'SmartParkingDB')
BEGIN
    CREATE DATABASE SmartParkingDB;
END;
GO

USE SmartParkingDB;
GO

-- ===========================================================================
-- PHASE 1: CLEAN START - DROP EXISTING TABLES IN CORRECT FK ORDER
-- ===========================================================================
IF OBJECT_ID('dbo.Payment', 'U')          IS NOT NULL DROP TABLE dbo.Payment;
IF OBJECT_ID('dbo.Parking_log', 'U')       IS NOT NULL DROP TABLE dbo.Parking_log;
IF OBJECT_ID('dbo.Parking_image', 'U')     IS NOT NULL DROP TABLE dbo.Parking_image;
IF OBJECT_ID('dbo.Booking', 'U')           IS NOT NULL DROP TABLE dbo.Booking;
IF OBJECT_ID('dbo.Ticket', 'U')            IS NOT NULL DROP TABLE dbo.Ticket;
IF OBJECT_ID('dbo.Pricing_rules', 'U')     IS NOT NULL DROP TABLE dbo.Pricing_rules;
IF OBJECT_ID('dbo.Parking_slot', 'U')      IS NOT NULL DROP TABLE dbo.Parking_slot;
IF OBJECT_ID('dbo.Vehicle', 'U')           IS NOT NULL DROP TABLE dbo.Vehicle;
IF OBJECT_ID('dbo.Customer', 'U')          IS NOT NULL DROP TABLE dbo.Customer;
IF OBJECT_ID('dbo.Payment_method', 'U')    IS NOT NULL DROP TABLE dbo.Payment_method;
IF OBJECT_ID('dbo.Parking_area', 'U')      IS NOT NULL DROP TABLE dbo.Parking_area;
IF OBJECT_ID('dbo.Vehicle_type', 'U')      IS NOT NULL DROP TABLE dbo.Vehicle_type;
IF OBJECT_ID('dbo.Staff', 'U')             IS NOT NULL DROP TABLE dbo.Staff;
GO

-- ===========================================================================
-- PHASE 2: TABLE CREATIONS
-- ===========================================================================

CREATE TABLE dbo.Staff (
    staff_id  INT IDENTITY(1,1) NOT NULL,
    name      NVARCHAR(100)     NOT NULL,
    phone     VARCHAR(15)        NULL,
    role      VARCHAR(20)       NOT NULL, -- admin, manager, attendant
    create_at DATETIME DEFAULT GETDATE() NOT NULL,
    status    VARCHAR(20) DEFAULT 'active' NOT NULL, -- active, inactive
    password  VARCHAR(255)      NOT NULL, -- Strict plain text storage
    CONSTRAINT PK_Staff PRIMARY KEY CLUSTERED (staff_id),
    CONSTRAINT UQ_Staff_Name UNIQUE (name)
);

CREATE TABLE dbo.Vehicle_type (
    vehicle_type_id INT IDENTITY(1,1) NOT NULL,
    type_name       NVARCHAR(50)     NOT NULL, -- Sedan, SUV, Motorbike
    price_per_hour  DECIMAL(10,2)    NOT NULL,
    price_per_day   DECIMAL(10,2)    NOT NULL,
    description     NVARCHAR(255)    NULL,
    CONSTRAINT PK_Vehicle_Type PRIMARY KEY CLUSTERED (vehicle_type_id)
);

CREATE TABLE dbo.Parking_area (
    area_id     INT IDENTITY(1,1) NOT NULL,
    area_code   VARCHAR(10)       NOT NULL, -- ZONE-A, ZONE-B
    description NVARCHAR(255)    NULL,
    CONSTRAINT PK_Parking_Area PRIMARY KEY CLUSTERED (area_id)
);

CREATE TABLE dbo.Payment_method (
    payment_method_id INT IDENTITY(1,1) NOT NULL,
    method_name       NVARCHAR(50)     NOT NULL, -- cash, qr, card
    is_active         BIT DEFAULT 1    NOT NULL,
    CONSTRAINT PK_Payment_Method PRIMARY KEY CLUSTERED (payment_method_id)
);

CREATE TABLE dbo.Customer (
    customer_id INT IDENTITY(1,1)  NOT NULL,
    name        NVARCHAR(100)      NOT NULL,
    phone       VARCHAR(15)        NULL,
    email       VARCHAR(100)       NULL,
    address     NVARCHAR(255)      NULL,
    created_at  DATETIME DEFAULT GETDATE() NOT NULL,
    CONSTRAINT PK_Customer PRIMARY KEY CLUSTERED (customer_id)
);

CREATE TABLE dbo.Vehicle (
    vehicle_id      INT IDENTITY(1,1)  NOT NULL,
    customer_id     INT                NOT NULL,
    vehicle_type_id INT                NOT NULL,
    license_plate   VARCHAR(20)        NOT NULL,
    model           NVARCHAR(50)       NULL,
    color           NVARCHAR(30)       NULL,
    created_at      DATETIME DEFAULT GETDATE() NOT NULL,
    CONSTRAINT PK_Vehicle PRIMARY KEY CLUSTERED (vehicle_id),
    CONSTRAINT UQ_Vehicle_Plate UNIQUE (license_plate),
    CONSTRAINT FK_Vehicle_Customer FOREIGN KEY (customer_id) REFERENCES dbo.Customer(customer_id),
    CONSTRAINT FK_Vehicle_Type FOREIGN KEY (vehicle_type_id) REFERENCES dbo.Vehicle_type(vehicle_type_id)
);

CREATE TABLE dbo.Parking_slot (
    slot_id    INT IDENTITY(1,1) NOT NULL,
    area_id    INT               NOT NULL,
    slot_code  VARCHAR(10)       NOT NULL, -- A-101, B-202
    slot_type  VARCHAR(20)       NOT NULL,
    status     VARCHAR(20) DEFAULT 'Empty' NOT NULL, -- Empty, Reserved, Occupied, Maintenance
    CONSTRAINT PK_Parking_Slot PRIMARY KEY CLUSTERED (slot_id),
    CONSTRAINT FK_Parking_Slot_Area FOREIGN KEY (area_id) REFERENCES dbo.Parking_area(area_id)
);

CREATE TABLE dbo.Pricing_rules (
    rule_id          INT IDENTITY(1,1) NOT NULL,
    vehicle_type_id  INT               NOT NULL,
    start_time       TIME              NOT NULL,
    end_time         TIME              NOT NULL,
    day_type         VARCHAR(20)       NOT NULL, -- Weekday, Weekend, Holiday
    first_hour_price DECIMAL(10,2)    NOT NULL,
    next_hour_price  DECIMAL(10,2)    NOT NULL,
    max_daily_price  DECIMAL(10,2)    NOT NULL,
    is_active        BIT DEFAULT 1     NOT NULL,
    effective_from   DATE              NOT NULL,
    effective_to     DATE              NOT NULL,
    CONSTRAINT PK_Pricing_Rules PRIMARY KEY CLUSTERED (rule_id),
    CONSTRAINT FK_Pricing_Rules_VehicleType FOREIGN KEY (vehicle_type_id) REFERENCES dbo.Vehicle_type(vehicle_type_id)
);

CREATE TABLE dbo.Ticket (
    ticket_id              INT IDENTITY(1,1)  NOT NULL,
    vehicle_id             INT                NOT NULL,
    check_in_by            INT                NOT NULL,
    entry_time             DATETIME DEFAULT GETDATE() NOT NULL,
    entry_image_url        VARCHAR(255)       NULL,
    license_plate_snapshot VARCHAR(20)        NULL,
    qr_code                VARCHAR(255)       NOT NULL,
    status                 VARCHAR(20) DEFAULT 'active' NOT NULL, -- active, completed, lost, canceled
    check_out_time         DATETIME           NULL,
    check_out_by           INT                NULL,
    note                   NVARCHAR(MAX)      NULL,
    CONSTRAINT PK_Ticket PRIMARY KEY CLUSTERED (ticket_id),
    CONSTRAINT UQ_Ticket_QR UNIQUE (qr_code),
    CONSTRAINT FK_Ticket_Vehicle FOREIGN KEY (vehicle_id) REFERENCES dbo.Vehicle(vehicle_id),
    CONSTRAINT FK_Ticket_Staff_In FOREIGN KEY (check_in_by) REFERENCES dbo.Staff(staff_id),
    CONSTRAINT FK_Ticket_Staff_Out FOREIGN KEY (check_out_by) REFERENCES dbo.Staff(staff_id)
);

CREATE TABLE dbo.Booking (
    booking_id       INT IDENTITY(1,1)  NOT NULL,
    license_plate    VARCHAR(20)        NOT NULL,
    vehicle_type_id  INT                NOT NULL,
    slot_id          INT                NOT NULL,
    target_time      DATETIME           NOT NULL,
    created_at       DATETIME DEFAULT GETDATE() NOT NULL,
    status           VARCHAR(20) DEFAULT 'active' NOT NULL, -- active, completed, canceled
    CONSTRAINT PK_Booking PRIMARY KEY CLUSTERED (booking_id),
    CONSTRAINT FK_Booking_Slot FOREIGN KEY (slot_id) REFERENCES dbo.Parking_slot(slot_id),
    CONSTRAINT FK_Booking_VehicleType FOREIGN KEY (vehicle_type_id) REFERENCES dbo.Vehicle_type(vehicle_type_id)
);

CREATE TABLE dbo.Parking_log (
    log_id      INT IDENTITY(1,1) NOT NULL,
    ticket_id   INT               NOT NULL,
    staff_id    INT               NOT NULL,
    action      VARCHAR(20)       NOT NULL, -- entry, exit
    action_time DATETIME DEFAULT GETDATE() NOT NULL,
    camera_id   VARCHAR(50)       NULL,
    note        NVARCHAR(MAX)     NULL,
    CONSTRAINT PK_Parking_Log PRIMARY KEY CLUSTERED (log_id),
    CONSTRAINT FK_Parking_Log_Ticket FOREIGN KEY (ticket_id) REFERENCES dbo.Ticket(ticket_id),
    CONSTRAINT FK_Parking_Log_Staff FOREIGN KEY (staff_id) REFERENCES dbo.Staff(staff_id)
);

CREATE TABLE dbo.Payment (
    payment_id        INT IDENTITY(1,1)  NOT NULL,
    ticket_id         INT                NOT NULL,
    payment_method_id INT                NOT NULL,
    amount            DECIMAL(10,2)      NOT NULL,
    discount          DECIMAL(10,2) DEFAULT 0.00 NOT NULL,
    final_amount      DECIMAL(10,2)      NOT NULL,
    payment_time      DATETIME DEFAULT GETDATE() NOT NULL,
    status            VARCHAR(20)        NOT NULL, -- paid, unpaid, refunded
    note              NVARCHAR(MAX)      NULL,
    CONSTRAINT PK_Payment PRIMARY KEY CLUSTERED (payment_id),
    CONSTRAINT FK_Payment_Ticket FOREIGN KEY (ticket_id) REFERENCES dbo.Ticket(ticket_id),
    CONSTRAINT FK_Payment_Method FOREIGN KEY (payment_method_id) REFERENCES dbo.Payment_method(payment_method_id)
);
GO

-- ===========================================================================
-- PHASE 3: PRODUCTION MOCK DATA POPULATION
-- ===========================================================================

-- 1. Seed Staff System Accounts with Explicit Plain Text Strings
INSERT INTO dbo.Staff (name, phone, role, status, password) VALUES 
(N'Nguyen Admin', '0901112223', 'admin', 'active', '123'),
(N'Tran Manager', '0912223334', 'manager', 'active', '345'),
(N'Le Attendant 1', '0923334445', 'attendant', 'active', '678'),
(N'Pham Attendant 2', '0934445556', 'attendant', 'active', '89');

-- 2. Seed Vehicle Categories & Pricing Scales
INSERT INTO dbo.Vehicle_type (type_name, price_per_hour, price_per_day, description) VALUES 
(N'Sedan', 15000.00, 120000.00, N'4-seater standard passenger cars'),
(N'SUV / Truck', 20000.00, 160000.00, N'Large 7-seater vehicles or pickups'),
(N'Motorbike', 5000.00, 40000.00, N'Two-wheeled vehicles');

-- 3. Seed Physical Layout Areas
INSERT INTO dbo.Parking_area (area_code, description) VALUES 
('ZONE-A', N'Ground Floor - Standard Sedans Only'),
('ZONE-B', N'Basement 1 - Large SUVs & Trucks'),
('ZONE-C', N'Rooftop Deck - Motorbike Parking Grid');

-- 4. Seed Integrated Payment Solutions
INSERT INTO dbo.Payment_method (method_name, is_active) VALUES 
(N'Cash Handover', 1),
(N'Momo QR Scan', 1),
(N'Bank Card Terminal', 1);

-- 5. Seed Customer Base Profiles
INSERT INTO dbo.Customer (name, phone, email, address) VALUES 
(N'Tran Van Hoang', '0988777666', 'hoang.tran@gmail.com', N'123 Nguyen Hue, District 1, HCMC'),
(N'Le Thi Mai', '0977666555', 'mai.le@yahoo.com', N'456 Le Loi, District 3, HCMC'),
(N'Pham Minh Duc', '0966555444', 'duc.pham@outlook.com', N'789 Dien Bien Phu, Binh Thanh, HCMC');

-- 6. Seed Registered Member Vehicles
INSERT INTO dbo.Vehicle (customer_id, vehicle_type_id, license_plate, model, color) VALUES 
(1, 1, '59A-123.45', 'Toyota Vios', 'Silver'),
(2, 2, '51G-987.65', 'Ford Ranger', 'Black'),
(3, 3, '59P-555.55', 'Honda SH 150i', 'White');

-- 7. Seed Physical Parking Slot Matrix Maps
INSERT INTO dbo.Parking_slot (area_id, slot_code, slot_type, status) VALUES 
(1, 'A-01', 'Sedan', 'Occupied'),
(1, 'A-02', 'Sedan', 'Empty'),
(1, 'A-03', 'Sedan', 'Maintenance'),
(2, 'B-01', 'SUV', 'Occupied'),
(2, 'B-02', 'SUV', 'Empty'),
(3, 'C-01', 'Motorbike', 'Occupied');

-- 8. Seed Dynamic Pricing Strategy Engine Matrices
INSERT INTO dbo.Pricing_rules (vehicle_type_id, start_time, end_time, day_type, first_hour_price, next_hour_price, max_daily_price, is_active, effective_from, effective_to) VALUES 
(1, '06:00:00', '22:00:00', 'Weekday', 15000.00, 10000.00, 120000.00, 1, '2026-01-01', '2026-12-31'),
(2, '06:00:00', '22:00:00', 'Weekday', 20000.00, 15000.00, 160000.00, 1, '2026-01-01', '2026-12-31'),
(3, '06:00:00', '22:00:00', 'Weekday', 5000.00,  3000.00,  40000.00,  1, '2026-01-01', '2026-12-31');

-- 9. Seed System Operational Parking Tickets
INSERT INTO dbo.Ticket (vehicle_id, check_in_by, entry_time, entry_image_url, license_plate_snapshot, qr_code, status, check_out_time, check_out_by, note) VALUES 
(1, 3, DATEADD(hour, -4, GETDATE()), 'https://s3.parking/img/entry1.jpg', '59A12345', 'QR_HASH_TOKEN_XYZ001', 'completed', DATEADD(hour, -1, GETDATE()), 3, N'Regular departure'),
(2, 3, DATEADD(hour, -2, GETDATE()), 'https://s3.parking/img/entry2.jpg', '51G98765', 'QR_HASH_TOKEN_XYZ002', 'active', NULL, NULL, NULL),
(3, 4, DATEADD(hour, -1, GETDATE()), 'https://s3.parking/img/entry3.jpg', '59P55555', 'QR_HASH_TOKEN_XYZ003', 'active', NULL, NULL, N'Rooftop bay assignment');

-- 10. Seed System Log Audit History Traces (Camera column completely removed)
INSERT INTO dbo.Parking_log (ticket_id, staff_id, action, action_time, note) VALUES  
(1, 3, 'entry', DATEADD(hour, -4, GETDATE()), N'Staff triggered manual gate actuation'),
(1, 3, 'exit', DATEADD(hour, -1, GETDATE()), N'Fee processed, gate released manually'),
(2, 3, 'entry', DATEADD(hour, -2, GETDATE()), N'Manual check-in log saved');

-- ===========================================================================
-- PHASE 4: VERIFICATION QUICK-RUNNER
-- ===========================================================================
SELECT N'SUCCESS' as [Deployment Status];
SELECT * FROM dbo.Staff;
GO
