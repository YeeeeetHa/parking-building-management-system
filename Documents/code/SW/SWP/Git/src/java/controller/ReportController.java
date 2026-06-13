/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

import utils.DbUtils;
import utils.TokenProvider;
import dao.StaffDAO;
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

/**
 *
 * @author Duong
 */
@WebServlet(name = "ReportController", urlPatterns = {"/api/v1/reports/summary"})
public class ReportController extends HttpServlet {
 
    private final StaffDAO staffDAO = new StaffDAO();
 
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
 
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        PrintWriter out = response.getWriter();
 
        // 1. Auth gate — token must be present and valid
        String token = request.getParameter("token");
        if (token == null || token.isEmpty() || !staffDAO.isValidToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"error\":\"Unauthorized. Please log in again.\"}");
            return;
        }
 
        // 2. Date params
        String startDate = request.getParameter("startDate");
        String endDate   = request.getParameter("endDate");
 
        if (startDate == null || endDate == null ||
            startDate.isEmpty() || endDate.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"startDate and endDate are required.\"}");
            return;
        }
 
        // Basic format check — prevent SQL injection via date strings
        if (!startDate.matches("\\d{4}-\\d{2}-\\d{2}") ||
            !endDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"Date format must be yyyy-MM-dd.\"}");
            return;
        }
 
        // endDate is inclusive — extend to end of day
        String endDateTime = endDate + " 23:59:59";
        String startDateTime = startDate + " 00:00:00";
 
        try (Connection conn = DbUtils.getConnection()) {
 
            // 3. Aggregate totals 
            long   totalServed   = 0;
            double grossEarnings = 0;
            long   paidCount     = 0;
            long   unpaidCount   = 0;
 
            String sqlTotals =
                "SELECT " +
                "  COUNT(ticket_id)             AS total_served, " +
                "  COALESCE(SUM(amount), 0)     AS gross_earnings, " +
                "  SUM(CASE WHEN status = 'paid'   THEN 1 ELSE 0 END) AS paid_cnt, " +
                "  SUM(CASE WHEN status <> 'paid'  THEN 1 ELSE 0 END) AS unpaid_cnt " +
                "FROM Payment " +
                "WHERE payment_time BETWEEN ? AND ?";
 
            try (PreparedStatement ps = conn.prepareStatement(sqlTotals)) {
                ps.setString(1, startDateTime);
                ps.setString(2, endDateTime);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalServed   = rs.getLong("total_served");
                        grossEarnings = rs.getDouble("gross_earnings");
                        paidCount     = rs.getLong("paid_cnt");
                        unpaidCount   = rs.getLong("unpaid_cnt");
                    }
                }
            }
 
            double avgPerTicket = totalServed > 0 ? grossEarnings / totalServed : 0;
 
            // 4. Day-by-day breakdown
            StringBuilder byDay = new StringBuilder("[");
            String sqlByDay =
                "SELECT " +
                "  CONVERT(date, payment_time)  AS day, " +
                "  COUNT(ticket_id)             AS tickets, " +
                "  COALESCE(SUM(amount), 0)     AS revenue " +
                "FROM Payment " +
                "WHERE payment_time BETWEEN ? AND ? " +
                "GROUP BY CONVERT(date, payment_time) " +
                "ORDER BY CONVERT(date, payment_time)";
 
            try (PreparedStatement ps = conn.prepareStatement(sqlByDay)) {
                ps.setString(1, startDateTime);
                ps.setString(2, endDateTime);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) byDay.append(",");
                        first = false;
                        byDay.append("{")
                             .append("\"day\":\"").append(rs.getString("day")).append("\",")
                             .append("\"tickets\":").append(rs.getLong("tickets")).append(",")
                             .append("\"revenue\":").append(rs.getDouble("revenue"))
                             .append("}");
                    }
                }
            }
            byDay.append("]");
 
            //5. Build final JSON
            out.print(String.format(
                "{" +
                "\"startDate\":\"%s\"," +
                "\"endDate\":\"%s\"," +
                "\"totalServed\":%d," +
                "\"grossEarnings\":%.2f," +
                "\"avgPerTicket\":%.2f," +
                "\"paid\":%d," +
                "\"unpaid\":%d," +
                "\"byDay\":%s" +
                "}",
                startDate, endDate,
                totalServed, grossEarnings, avgPerTicket,
                paidCount, unpaidCount,
                byDay.toString()
            ));
 
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"error\":\"Internal server error: " +
                e.getMessage().replace("\"","'") + "\"}");
        }
    }
 
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) {
        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        res.setStatus(HttpServletResponse.SC_OK);
    }
}