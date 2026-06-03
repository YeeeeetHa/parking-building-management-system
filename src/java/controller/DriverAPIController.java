/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

import dao.DriverDAO;
import dto.Driver;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/*
 * DriverAPIController — handles POST /api/v1/drivers/register
 *
 * This is the backend entry point for the operator-facing driver registration form.
 * The frontend (customer-login.html) submits a form POST here with five fields: fullName, phone, email, address, licensePlate
 *
 * Response contract (plain text, not JSON — keep in sync with customer-login.html JS):
 *   201 Created  → "Driver registered successfully"   (everything went fine)
 *   409 Conflict → "License plate already exists"     (DB unique constraint UQ_Vehicle_Plate hit)
 *   500          → wrapped ServletException           (unexpected DB or system error)
 */
@WebServlet(name = "DriverAPIController", urlPatterns = {"/api/v1/drivers/register"})
public class DriverAPIController extends HttpServlet {

    // doPost is triggered when the registration form hits Submit
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        // Pull each form field out of the request — the frontend sends these as
        // application/x-www-form-urlencoded, which is why getParameter() works here
        String fullName = request.getParameter("fullName");
        String phone = request.getParameter("phone");
        String email = request.getParameter("email");       // optional — can be null/empty
        String address = request.getParameter("address");   // optional — can be null/empty
        String licensePlate = request.getParameter("licensePlate");
        String vehicleTypeStr = request.getParameter("vehicleType");
        int vehicleType = 1; // Default to Sedan if not provided
        try {
            if (vehicleTypeStr != null && !vehicleTypeStr.isEmpty()) {
                vehicleType = Integer.parseInt(vehicleTypeStr);
            }
        } catch (NumberFormatException e) {
            // ignore, keep default
        }
        // Pack everything into a DriverDTO so the DAO layer doesn't need to know
        // anything about the HTTP request — clean separation of concerns
        Driver driver = new Driver();
        driver.setFullName(fullName);
        driver.setPhone(phone);
        driver.setEmail(email);
        driver.setAddress(address);
        driver.setLicensePlate(licensePlate);
        driver.setVehicleType(vehicleType);
        DriverDAO dao = new DriverDAO();
        try {
            // Hand off to the DAO which runs the actual INSERT statement
            boolean success = dao.registerDriver(driver);
            if (success) {
                // 201 = "resource created" — standard REST response for a new record
                response.setStatus(HttpServletResponse.SC_CREATED);
                response.getWriter().write("Driver registered successfully");
            }
        } catch (SQLException ex) {
            // Check if the error is specifically from the license plate uniqueness constraint.
            // UQ_Vehicle_Plate is defined in the DB schema (SmartParkingDB.sql).
            // We return 409 Conflict so the frontend can show a specific "plate already exists" message.
            if (ex.getMessage().contains("UQ_Vehicle_Plate")) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("License plate already exists");
            } else {
                // Any other SQL error (bad connection, schema mismatch, etc.) bubbles up as 500
                throw new ServletException(ex);
            }
        } catch (Exception ex) {
            // Catch-all for non-SQL errors (e.g. DriverDAO constructor failure)
            throw new ServletException(ex);
        }
    }

    // handles GET /api/v1/drivers/register to retrieve all dynamic vehicle categories
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        DriverDAO dao = new DriverDAO();
        try {
            java.util.List<String[]> list = dao.getVehicleTypes();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                String[] vt = list.get(i);
                json.append(String.format("{\"id\":%s,\"name\":\"%s\"}", vt[0], vt[1].replace("\"", "\\\"")));
                if (i < list.size() - 1) {
                    json.append(",");
                }
            }
            json.append("]");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(json.toString());
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }
}