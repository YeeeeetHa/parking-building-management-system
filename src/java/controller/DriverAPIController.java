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
import utils.InputValidator;

@WebServlet(name = "DriverAPIController", urlPatterns = {"/api/v1/drivers/register"})
public class DriverAPIController extends HttpServlet {

    protected void doPost(HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        String fullName = request.getParameter("fullName");
        String phone = request.getParameter("phone");
        String email = request.getParameter("email");    
        String address = request.getParameter("address");
        String licensePlate = request.getParameter("licensePlate");
        String vehicleTypeStr = request.getParameter("vehicleType");
        int vehicleType = 1;
        try {
            if (vehicleTypeStr != null && !vehicleTypeStr.isEmpty()) {
                vehicleType = Integer.parseInt(vehicleTypeStr);
            }
        } catch (NumberFormatException e) {
        }

        if (!InputValidator.isValidLicensePlate(licensePlate)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid license plate format");
            return;
        }

        Driver driver = new Driver();
        driver.setFullName(fullName);
        driver.setPhone(phone);
        driver.setEmail(email);
        driver.setAddress(address);
        driver.setLicensePlate(licensePlate);
        driver.setVehicleType(vehicleType);
        DriverDAO dao = new DriverDAO();
        try {
            boolean success = dao.registerDriver(driver);
            if (success) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                response.getWriter().write("Driver registered successfully");
            }
        } catch (SQLException ex) {
            if (ex.getMessage().contains("UQ_Vehicle_Plate")) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("License plate already exists");
            } else {
                throw new ServletException(ex);
            }
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }

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