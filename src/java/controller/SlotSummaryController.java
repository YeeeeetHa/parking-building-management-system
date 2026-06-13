package controller;

import utils.DbUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/*
 * SlotSummaryController — public endpoint for the main home page and customer portal.
 *
 * Returns a top-level count of occupied/available slots, plus per-zone breakdowns.
 * No authentication required — it's the same data anyone driving past could see
 * on the parking sign out front.
 *
 * Maps to: GET /api/v1/slots/summary
 */
@WebServlet(name = "SlotSummaryController", urlPatterns = {"/api/v1/slots/summary"})
public class SlotSummaryController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        try (Connection conn = DbUtils.getConnection()) {

            // Grab overall totals in one query
            int total = 0, occupied = 0, reserved = 0;
            String sqlTotal = "SELECT " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN status = 'Occupied' THEN 1 ELSE 0 END) AS occupied, " +
                "SUM(CASE WHEN status = 'Reserved' THEN 1 ELSE 0 END) AS reserved " +
                "FROM dbo.Parking_slot";
            try (PreparedStatement ps = conn.prepareStatement(sqlTotal);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    total    = rs.getInt("total");
                    occupied = rs.getInt("occupied");
                    reserved = rs.getInt("reserved");
                }
            }
            int available = Math.max(0, total - occupied - reserved);

            // Per-zone breakdown for the zone bars on the home page
            StringBuilder zonesJson = new StringBuilder("[");
            String sqlZones = "SELECT a.area_code, " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN s.status = 'Occupied' THEN 1 ELSE 0 END) AS occupied " +
                "FROM dbo.Parking_slot s " +
                "JOIN dbo.Parking_area a ON s.area_id = a.area_id " +
                "GROUP BY a.area_code ORDER BY a.area_code";
            try (PreparedStatement ps = conn.prepareStatement(sqlZones);
                 ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) zonesJson.append(",");
                    first = false;
                    zonesJson.append("{")
                        .append("\"name\":\"").append(rs.getString("area_code")).append("\",")
                        .append("\"total\":").append(rs.getInt("total")).append(",")
                        .append("\"occupied\":").append(rs.getInt("occupied"))
                        .append("}");
                }
            }
            zonesJson.append("]");

            PrintWriter out = response.getWriter();
            out.print("{" +
                "\"total\":" + total + "," +
                "\"occupied\":" + occupied + "," +
                "\"reserved\":" + reserved + "," +
                "\"available\":" + available + "," +
                "\"zones\":" + zonesJson.toString() +
                "}");

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
