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
import java.sql.ResultSet;

@WebServlet(name = "StaffDashboardController", urlPatterns = {"/api/v1/staff/dashboard"})
public class StaffDashboardController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            try (Connection conn = DbUtils.getConnection()) {
                StringBuilder json = new StringBuilder();
                // Slots
                int totalSlots = 0;
                int occupied = 0;
                String sqlTotal = "SELECT COUNT(*) AS cnt FROM Parking_slot";
                try (PreparedStatement ps = conn.prepareStatement(sqlTotal); ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) totalSlots = rs.getInt("cnt");
                }
                String sqlOcc = "SELECT COUNT(*) AS cnt FROM Parking_slot WHERE status = 'Occupied'";
                try (PreparedStatement ps = conn.prepareStatement(sqlOcc); ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) occupied = rs.getInt("cnt");
                }

                int available = Math.max(0, totalSlots - occupied);

                // Total revenue (all-time)
                double totalRevenue = 0.0;
                String sqlRevenue = "SELECT COALESCE(SUM(amount),0) AS s FROM Payment";
                try (PreparedStatement ps = conn.prepareStatement(sqlRevenue); ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) totalRevenue = rs.getDouble("s");
                }

                // Ticket status counts
                StringBuilder ticketStatusJson = new StringBuilder();
                ticketStatusJson.append("{");
                String sqlTicketStatus = "SELECT status, COUNT(*) AS cnt FROM Ticket GROUP BY status";
                try (PreparedStatement ps = conn.prepareStatement(sqlTicketStatus); ResultSet rs = ps.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) ticketStatusJson.append(",");
                        first = false;
                        ticketStatusJson.append("\"").append(rs.getString("status")).append("\":").append(rs.getInt("cnt"));
                    }
                }
                ticketStatusJson.append("}");

                // Current active tickets
                StringBuilder ticketsJson = new StringBuilder();
                ticketsJson.append("[");
                String sqlActiveTickets = "SELECT t.ticket_id, v.license_plate, t.entry_time, t.status FROM Ticket t JOIN Vehicle v ON t.vehicle_id = v.vehicle_id WHERE t.status = 'active' ORDER BY t.entry_time DESC";
                try (PreparedStatement ps = conn.prepareStatement(sqlActiveTickets); ResultSet rs = ps.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) ticketsJson.append(",");
                        first = false;
                        ticketsJson.append("{");
                        ticketsJson.append("\"ticket_id\":").append(rs.getInt("ticket_id")).append(",");
                        ticketsJson.append("\"license_plate\":\"").append(rs.getString("license_plate")).append("\",");
                        ticketsJson.append("\"entry_time\":\"").append(rs.getTimestamp("entry_time")).append("\",");
                        ticketsJson.append("\"area\":null,");
                        ticketsJson.append("\"status\":\"").append(rs.getString("status")).append("\"");
                        ticketsJson.append("}");
                    }
                }
                ticketsJson.append("]");

                // Payments today
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

                // Build final JSON
                json.append("{");
                json.append("\"slots\":{");
                json.append("\"total\":").append(totalSlots).append(",");
                json.append("\"occupied\":").append(occupied).append(",");
                json.append("\"available\":").append(available).append("}").append(",");
                json.append("\"totalRevenue\":").append(totalRevenue).append(",");
                json.append("\"ticketStatus\":").append(ticketStatusJson.toString()).append(",");
                json.append("\"currentTickets\":").append(ticketsJson.toString()).append(",");
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
