/*
 * Staff dashboard data API
 */
package controller;

import utils.DbUtils;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import dao.StaffDAO;
import java.sql.ResultSet;

@WebServlet(name = "StaffDashboardController", urlPatterns = {"/api/v1/staff/dashboard"})
public class StaffDashboardController extends HttpServlet {

    private boolean authenticate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing or invalid Authorization header");
            return false;
        }
        String token = authHeader.substring(7);
        StaffDAO staffDao = new StaffDAO();
        if (!staffDao.isValidToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or expired session token");
            return false;
        }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        if (!authenticate(request, response)) return;
        
        try (PrintWriter out = response.getWriter()) {
            try (Connection conn = DbUtils.getConnection()) {
                StringBuilder json = new StringBuilder();
                
                int totalSlots = 0;
                int occupied = 0;
                
                String sqlTotal = "SELECT COUNT(*) AS cnt FROM dbo.Parking_slot";
                try (PreparedStatement ps = conn.prepareStatement(sqlTotal); ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) totalSlots = rs.getInt("cnt");
                }
                
                String sqlOcc = "SELECT COUNT(*) AS cnt FROM dbo.Parking_slot WHERE status = 'Occupied'";
                try (PreparedStatement ps = conn.prepareStatement(sqlOcc); ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) occupied = rs.getInt("cnt");
                }

                int available = Math.max(0, totalSlots - occupied);

                double totalRevenue = 0.0;
                String sqlRevenue = "SELECT COALESCE(SUM(amount),0) AS s FROM Payment";
                try (PreparedStatement ps = conn.prepareStatement(sqlRevenue); ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) totalRevenue = rs.getDouble("s");
                }

                StringBuilder zonesJson = new StringBuilder();
                zonesJson.append("[");
                String sqlZones = "SELECT a.area_code, COUNT(s.slot_id) AS total_slots, " +
                                  "SUM(CASE WHEN s.status = 'Occupied' THEN 1 ELSE 0 END) AS occupied_slots " +
                                  "FROM dbo.Parking_slot s " +
                                  "JOIN dbo.Parking_area a ON s.area_id = a.area_id " +
                                  "GROUP BY a.area_code ORDER BY a.area_code";
                try (PreparedStatement ps = conn.prepareStatement(sqlZones); ResultSet rs = ps.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) zonesJson.append(",");
                        first = false;
                        zonesJson.append("{");
                        zonesJson.append("\"area_code\":\"").append(rs.getString("area_code")).append("\",");
                        zonesJson.append("\"total\":").append(rs.getInt("total_slots")).append(",");
                        zonesJson.append("\"occupied\":").append(rs.getInt("occupied_slots"));
                        zonesJson.append("}");
                    }
                }
                zonesJson.append("]");

                StringBuilder vehicleTypesJson = new StringBuilder();
                vehicleTypesJson.append("{");
                String sqlVehicles = "SELECT t.type_name, COUNT(*) AS cnt " +
                                     "FROM dbo.Booking b " +
                                     "JOIN dbo.Vehicle_type t ON b.vehicle_type_id = t.vehicle_type_id " +
                                     "WHERE b.status IN ('occupied', 'active') " +
                                     "GROUP BY t.type_name";
                int totalActive = 0;
                int cars = 0;
                int motorbikes = 0;
                try (PreparedStatement ps = conn.prepareStatement(sqlVehicles); ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("type_name");
                        int count = rs.getInt("cnt");
                        totalActive += count;
                        if (type != null && type.toLowerCase().contains("motorbike")) {
                            motorbikes += count;
                        } else {
                            cars += count;
                        }
                    }
                }
                vehicleTypesJson.append("\"total\":").append(totalActive).append(",");
                vehicleTypesJson.append("\"cars\":").append(cars).append(",");
                vehicleTypesJson.append("\"motorbikes\":").append(motorbikes);
                vehicleTypesJson.append("}");

                double todayAmount = 0.0;
                int todayCount = 0;
                int paidCount = 0;
                int unpaidCount = 0;
                String sqlPaymentsToday = "SELECT COALESCE(SUM(amount),0) AS s, COUNT(*) AS cnt, SUM(CASE WHEN status = 'paid' THEN 1 ELSE 0 END) AS paid_cnt, SUM(CASE WHEN status <> 'paid' THEN 1 ELSE 0 END) AS unpaid_cnt FROM Payment WHERE CONVERT(date, payment_time) = CONVERT(date, GETDATE())";
                try (PreparedStatement ps = conn.prepareStatement(sqlPaymentsToday); ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        todayAmount = rs.getDouble("s");
                        todayCount = rs.getInt("cnt");
                        paidCount = rs.getInt("paid_cnt");
                        unpaidCount = rs.getInt("unpaid_cnt");
                    }
                }

                json.append("{");
                json.append("\"slots\":{");
                json.append("\"total\":").append(totalSlots).append(",");
                json.append("\"occupied\":").append(occupied).append(",");
                json.append("\"available\":").append(available).append("}").append(",");
                json.append("\"totalRevenue\":").append(totalRevenue).append(",");
                json.append("\"zones\":").append(zonesJson.toString()).append(",");
                json.append("\"vehicleTypes\":").append(vehicleTypesJson.toString()).append(",");
                json.append("\"paymentsToday\":{");
                json.append("\"totalAmount\":").append(todayAmount).append(",");
                json.append("\"transactions\":").append(todayCount).append(",");
                json.append("\"paid\":").append(paidCount).append(",");
                json.append("\"unpaid\":").append(unpaidCount).append("}");
                json.append("}");

                out.print(json.toString());

            } catch (Exception e) {
                e.printStackTrace();
                response.setStatus(500);
            }
        }
    }
}