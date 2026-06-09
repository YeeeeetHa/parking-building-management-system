/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import dto.Driver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import utils.DbUtils;
import utils.InputValidator;

/*
 * DriverDAO — Data Access Object for driver/vehicle registration
 *
 * Sits between DriverAPIController (HTTP layer) and the database.
 * Only job here is to run the INSERT for a new driver record.
 * Any SQL exceptions are thrown upward — the controller decides what HTTP status to send back.
 */
public class DriverDAO {

    /*
     * registerDriver inserts a new row into the Driver table using a PreparedStatement.
     * Using PreparedStatement (not Statement) is important — it protects against SQL injection
     * since the values are bound as parameters, not concatenated into the query string.
     * Throws Exception (not caught here) so the caller (DriverAPIController) can inspect
     * the SQLException message and decide whether to return 409 or 500.
     */
    public boolean registerDriver(Driver driver) throws Exception {
        if (driver == null) {
            throw new IllegalArgumentException("Driver cannot be null");
        }
        if (!InputValidator.isValidLicensePlate(driver.getLicensePlate())) {
            throw new IllegalArgumentException("Invalid license plate");
        }

        Connection conn = null;
        PreparedStatement psCustomer = null;
        PreparedStatement psVehicle = null;
        ResultSet rs = null;
        boolean success = false;

        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false); // start transaction
            // 1. Insert into Customer
            String sqlCustomer = "INSERT INTO Customer(name, phone, email, address) VALUES(?,?,?,?)";
            // Use Statement.RETURN_GENERATED_KEYS to get the generated customer_id
            psCustomer = conn.prepareStatement(sqlCustomer, Statement.RETURN_GENERATED_KEYS);
            psCustomer.setString(1, driver.getFullName());
            psCustomer.setString(2, driver.getPhone());
            psCustomer.setString(3, driver.getEmail());
            psCustomer.setString(4, driver.getAddress());
            int affected = psCustomer.executeUpdate();
            if (affected > 0) {
                rs = psCustomer.getGeneratedKeys();
                if (rs.next()) {
                    int customerId = rs.getInt(1);
                    // 2. Insert into Vehicle
                    String sqlVehicle = "INSERT INTO Vehicle(customer_id, vehicle_type_id, license_plate) VALUES(?,?,?)";
                    psVehicle = conn.prepareStatement(sqlVehicle);
                    psVehicle.setInt(1, customerId);
                    psVehicle.setInt(2, driver.getVehicleType());
                    psVehicle.setString(3, driver.getLicensePlate());
                    success = psVehicle.executeUpdate() > 0;
                }
            }
            if (success) {
                conn.commit();
            } else {
                conn.rollback();
            }
            return success;
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception ex) {
                }
            }
            throw e;
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (psCustomer != null) {
                psCustomer.close();
            }
            if (psVehicle != null) {
                psVehicle.close();
            }
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /*
     * getVehicleTypes retrieves all vehicle types currently detailed in the database.
     * Returns a list of String arrays, where each array contains the vehicle type ID and name.
     * Throws Exception to be handled or bubbled up by the caller.
     */
    public java.util.List<String[]> getVehicleTypes() throws Exception {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        java.util.List<String[]> list = new java.util.ArrayList<>();
        try {
            con = DbUtils.getConnection();
            String sql = "SELECT vehicle_type_id, type_name FROM Vehicle_type ORDER BY vehicle_type_id";
            ps = con.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("vehicle_type_id")),
                    rs.getString("type_name")
                });
            }
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ps != null) {
                ps.close();
            }
            if (con != null) {
                con.close();
            }
        }
        return list;
    }
}
