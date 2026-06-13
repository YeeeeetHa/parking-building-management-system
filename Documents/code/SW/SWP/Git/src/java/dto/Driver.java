/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dto;

/*
 * DriverDTO (Data Transfer Object) — carries driver/vehicle registration data
 * from DriverAPIController down to DriverDAO.
 *
 * Each field maps to a column in the DB:
 *   fullName     → Driver.full_name   (NVARCHAR 100)
 *   phone        → Driver.phone       (VARCHAR 15, nullable)
 *   email        → Driver.email       (VARCHAR 100, nullable)
 *   address      → Driver.address     (NVARCHAR 255, nullable)
 *   licensePlate → Vehicle.license_plate (VARCHAR 20, must be unique — UQ_Vehicle_Plate)
 *
 * This is just a plain data holder — no logic lives here.
 */
public class Driver {
    private String fullName;
    private String phone;
    private String email;
    private String address;
    private String licensePlate;
    private int vehicleType;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }
    
    public int getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(int vehicleType) {
        this.vehicleType = vehicleType;
    }
}