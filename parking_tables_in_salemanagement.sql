USE SaleManagement;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.schemas
    WHERE name = 'Parking'
)
BEGIN
    EXEC('CREATE SCHEMA Parking');
END;
GO

CREATE TABLE Parking.Customer (
    customer_id INT IDENTITY(1,1) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(15) NULL,
    email VARCHAR(100) NULL,
    address VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT PK_Parking_Customer PRIMARY KEY (customer_id)
);
GO

CREATE TABLE Parking.Vehicle_Type (
    vehicle_type_id INT IDENTITY(1,1) NOT NULL,
    type_name VARCHAR(50) NOT NULL,
    price_per_hour DECIMAL(10,2) NOT NULL,
    price_per_day DECIMAL(10,2) NOT NULL,
    description VARCHAR(255) NULL,

    CONSTRAINT PK_Parking_Vehicle_Type PRIMARY KEY (vehicle_type_id)
);
GO

CREATE TABLE Parking.Parking_Area (
    area_id INT IDENTITY(1,1) NOT NULL,
    area_name VARCHAR(50) NOT NULL,
    description VARCHAR(255) NULL,

    CONSTRAINT PK_Parking_Area PRIMARY KEY (area_id)
);
GO

CREATE TABLE Parking.Parking_Slot (
    slot_id INT IDENTITY(1,1) NOT NULL,
    area_id INT NOT NULL,
    slot_code VARCHAR(10) NOT NULL,
    slot_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Empty',

    CONSTRAINT PK_Parking_Slot PRIMARY KEY (slot_id),
    CONSTRAINT UQ_Parking_Slot_Area_Code UNIQUE (area_id, slot_code),
    CONSTRAINT FK_Parking_Area_Parking_Slot
        FOREIGN KEY (area_id)
        REFERENCES Parking.Parking_Area(area_id),
    CONSTRAINT CHK_Parking_Slot_Status
        CHECK (status IN ('Empty', 'Reserved', 'Occupied', 'Maintenance'))
);
GO

CREATE TABLE Parking.Staff (
    staff_id INT IDENTITY(1,1) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(15) NULL,
    email VARCHAR(100) NULL,
    role VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT PK_Parking_Staff PRIMARY KEY (staff_id)
);
GO

CREATE TABLE Parking.Vehicle (
    vehicle_id INT IDENTITY(1,1) NOT NULL,
    customer_id INT NOT NULL,
    vehicle_type_id INT NOT NULL,
    license_plate VARCHAR(20) NOT NULL,
    model VARCHAR(50) NULL,
    color VARCHAR(30) NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT PK_Parking_Vehicle PRIMARY KEY (vehicle_id),
    CONSTRAINT UQ_Parking_Vehicle_License_Plate UNIQUE (license_plate),
    CONSTRAINT FK_Parking_Customer_Vehicle
        FOREIGN KEY (customer_id)
        REFERENCES Parking.Customer(customer_id),
    CONSTRAINT FK_Parking_Vehicle_Type_Vehicle
        FOREIGN KEY (vehicle_type_id)
        REFERENCES Parking.Vehicle_Type(vehicle_type_id)
);
GO

CREATE TABLE Parking.Ticket (
    ticket_id INT IDENTITY(1,1) NOT NULL,
    vehicle_id INT NOT NULL,
    slot_id INT NOT NULL,
    check_in_by INT NOT NULL,
    entry_time DATETIME NOT NULL DEFAULT GETDATE(),
    entry_image_url VARCHAR(255) NULL,
    license_plate_snapshot VARCHAR(20) NULL,
    qr_code VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Active',
    check_out_time DATETIME NULL,
    check_out_by INT NULL,
    note VARCHAR(MAX) NULL,

    CONSTRAINT PK_Parking_Ticket PRIMARY KEY (ticket_id),
    CONSTRAINT UQ_Parking_Ticket_QR_Code UNIQUE (qr_code),
    CONSTRAINT FK_Parking_Vehicle_Ticket
        FOREIGN KEY (vehicle_id)
        REFERENCES Parking.Vehicle(vehicle_id),
    CONSTRAINT FK_Parking_Slot_Ticket
        FOREIGN KEY (slot_id)
        REFERENCES Parking.Parking_Slot(slot_id),
    CONSTRAINT FK_Parking_Staff_Ticket_Check_In
        FOREIGN KEY (check_in_by)
        REFERENCES Parking.Staff(staff_id),
    CONSTRAINT FK_Parking_Staff_Ticket_Check_Out
        FOREIGN KEY (check_out_by)
        REFERENCES Parking.Staff(staff_id),
    CONSTRAINT CHK_Parking_Ticket_Status
        CHECK (status IN ('Active', 'Completed', 'Lost', 'Canceled'))
);
GO

CREATE TABLE Parking.Payment_Method (
    payment_method_id INT IDENTITY(1,1) NOT NULL,
    method_name VARCHAR(50) NOT NULL,

    CONSTRAINT PK_Parking_Payment_Method PRIMARY KEY (payment_method_id),
    CONSTRAINT UQ_Parking_Payment_Method_Name UNIQUE (method_name)
);
GO

CREATE TABLE Parking.Payment (
    payment_id INT IDENTITY(1,1) NOT NULL,
    ticket_id INT NOT NULL,
    payment_method_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    discount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    final_amount DECIMAL(10,2) NOT NULL,
    payment_time DATETIME NOT NULL DEFAULT GETDATE(),
    status VARCHAR(20) NOT NULL DEFAULT 'Unpaid',
    note VARCHAR(MAX) NULL,

    CONSTRAINT PK_Parking_Payment PRIMARY KEY (payment_id),
    CONSTRAINT FK_Parking_Ticket_Payment
        FOREIGN KEY (ticket_id)
        REFERENCES Parking.Ticket(ticket_id),
    CONSTRAINT FK_Parking_Payment_Method_Payment
        FOREIGN KEY (payment_method_id)
        REFERENCES Parking.Payment_Method(payment_method_id),
    CONSTRAINT CHK_Parking_Payment_Status
        CHECK (status IN ('Paid', 'Unpaid', 'Refunded')),
    CONSTRAINT CHK_Parking_Payment_Amounts
        CHECK (amount >= 0 AND discount >= 0 AND final_amount >= 0)
);
GO

CREATE INDEX IX_Parking_Vehicle_Customer_ID ON Parking.Vehicle(customer_id);
CREATE INDEX IX_Parking_Vehicle_Vehicle_Type_ID ON Parking.Vehicle(vehicle_type_id);
CREATE INDEX IX_Parking_Slot_Area_ID ON Parking.Parking_Slot(area_id);
CREATE INDEX IX_Parking_Ticket_Vehicle_ID ON Parking.Ticket(vehicle_id);
CREATE INDEX IX_Parking_Ticket_Slot_ID ON Parking.Ticket(slot_id);
CREATE INDEX IX_Parking_Ticket_Check_In_By ON Parking.Ticket(check_in_by);
CREATE INDEX IX_Parking_Ticket_Check_Out_By ON Parking.Ticket(check_out_by);
CREATE INDEX IX_Parking_Payment_Ticket_ID ON Parking.Payment(ticket_id);
CREATE INDEX IX_Parking_Payment_Method_ID ON Parking.Payment(payment_method_id);
GO
