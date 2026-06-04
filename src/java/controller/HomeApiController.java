/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

/**
 *
 * @author Duong
 */

import utils.DbUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet(name = "HomeApiController", urlPatterns = {"/api/v1/home/summary"})
public class HomeApiController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        PrintWriter out = response.getWriter();

        try (Connection conn = DbUtils.getConnection()) {

            // ── 1. Slot summary ──────────────────────────────────────────────
            int totalSlots = 0, occupied = 0, empty = 0, maintenance = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, COUNT(*) AS cnt FROM dbo.Parking_slot GROUP BY status");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cnt = rs.getInt("cnt");
                    totalSlots += cnt;
                    switch (rs.getString("status").trim()) {
                        case "Occupied":    occupied    += cnt; break;
                        case "Empty":       empty       += cnt; break;
                        case "Maintenance": maintenance += cnt; break;
                    }
                }
            }

            // ── 2. Zone breakdown ────────────────────────────────────────────
            StringBuilder zonesJson = new StringBuilder("[");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT pa.area_code, pa.description, " +
                    "SUM(CASE WHEN ps.status='Occupied' THEN 1 ELSE 0 END) AS occ, " +
                    "SUM(CASE WHEN ps.status='Empty'    THEN 1 ELSE 0 END) AS emp, " +
                    "COUNT(*) AS total " +
                    "FROM dbo.Parking_slot ps " +
                    "JOIN dbo.Parking_area pa ON ps.area_id = pa.area_id " +
                    "GROUP BY pa.area_code, pa.description ORDER BY pa.area_code");
                 ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) zonesJson.append(",");
                    first = false;
                    zonesJson.append("{")
                        .append("\"code\":\"").append(esc(rs.getString("area_code"))).append("\",")
                        .append("\"desc\":\"").append(esc(rs.getString("description"))).append("\",")
                        .append("\"occupied\":").append(rs.getInt("occ")).append(",")
                        .append("\"empty\":").append(rs.getInt("emp")).append(",")
                        .append("\"total\":").append(rs.getInt("total"))
                        .append("}");
                }
            }
            zonesJson.append("]");

            // ── 3. Active vehicles currently parked ──────────────────────────
            StringBuilder vehiclesJson = new StringBuilder("[");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT t.ticket_id, t.license_plate_snapshot, t.entry_time, " +
                    "v.model, v.color, vt.type_name, " +
                    "c.name AS customer_name, c.phone AS customer_phone " +
                    "FROM dbo.Ticket t " +
                    "JOIN dbo.Vehicle v   ON t.vehicle_id = v.vehicle_id " +
                    "JOIN dbo.Vehicle_type vt ON v.vehicle_type_id = vt.vehicle_type_id " +
                    "JOIN dbo.Customer c  ON v.customer_id = c.customer_id " +
                    "WHERE t.status = 'active' " +
                    "ORDER BY t.entry_time DESC");
                 ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) vehiclesJson.append(",");
                    first = false;
                    Timestamp entry = rs.getTimestamp("entry_time");
                    long mins = entry != null
                        ? (System.currentTimeMillis() - entry.getTime()) / 60000 : 0;
                    String dur = mins < 60
                        ? mins + "m"
                        : (mins / 60) + "h " + (mins % 60) + "m";
                    vehiclesJson.append("{")
                        .append("\"ticket_id\":").append(rs.getInt("ticket_id")).append(",")
                        .append("\"plate\":\"").append(esc(rs.getString("license_plate_snapshot"))).append("\",")
                        .append("\"model\":\"").append(esc(rs.getString("model"))).append("\",")
                        .append("\"color\":\"").append(esc(rs.getString("color"))).append("\",")
                        .append("\"type\":\"").append(esc(rs.getString("type_name"))).append("\",")
                        .append("\"customer\":\"").append(esc(rs.getString("customer_name"))).append("\",")
                        .append("\"phone\":\"").append(esc(rs.getString("customer_phone"))).append("\",")
                        .append("\"duration\":\"").append(dur).append("\"")
                        .append("}");
                }
            }
            vehiclesJson.append("]");

            // ── 4. Pricing from Vehicle_type ─────────────────────────────────
            StringBuilder pricingJson = new StringBuilder("[");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT type_name, price_per_hour, price_per_day, description " +
                    "FROM dbo.Vehicle_type ORDER BY vehicle_type_id");
                 ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) pricingJson.append(",");
                    first = false;
                    pricingJson.append("{")
                        .append("\"type\":\"").append(esc(rs.getString("type_name"))).append("\",")
                        .append("\"per_hour\":").append(rs.getBigDecimal("price_per_hour")).append(",")
                        .append("\"per_day\":").append(rs.getBigDecimal("price_per_day")).append(",")
                        .append("\"desc\":\"").append(esc(rs.getString("description"))).append("\"")
                        .append("}");
                }
            }
            pricingJson.append("]");

            // ── 5. Last 5 completed tickets ──────────────────────────────────
            StringBuilder recentJson = new StringBuilder("[");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT TOP 5 t.license_plate_snapshot, v.model, vt.type_name, " +
                    "p.final_amount, pm.method_name " +
                    "FROM dbo.Ticket t " +
                    "JOIN dbo.Vehicle v    ON t.vehicle_id = v.vehicle_id " +
                    "JOIN dbo.Vehicle_type vt ON v.vehicle_type_id = vt.vehicle_type_id " +
                    "LEFT JOIN dbo.Payment p  ON p.ticket_id = t.ticket_id " +
                    "LEFT JOIN dbo.Payment_method pm ON p.payment_method_id = pm.payment_method_id " +
                    "WHERE t.status = 'completed' ORDER BY t.check_out_time DESC");
                 ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) recentJson.append(",");
                    first = false;
                    recentJson.append("{")
                        .append("\"plate\":\"").append(esc(rs.getString("license_plate_snapshot"))).append("\",")
                        .append("\"model\":\"").append(esc(rs.getString("model"))).append("\",")
                        .append("\"type\":\"").append(esc(rs.getString("type_name"))).append("\",")
                        .append("\"amount\":").append(rs.getBigDecimal("final_amount") != null
                            ? rs.getBigDecimal("final_amount") : 0).append(",")
                        .append("\"method\":\"").append(esc(rs.getString("method_name"))).append("\"")
                        .append("}");
                }
            }
            recentJson.append("]");

            // ── Final JSON ───────────────────────────────────────────────────
            out.print("{" +
                "\"total\":"       + totalSlots    + "," +
                "\"occupied\":"    + occupied      + "," +
                "\"empty\":"       + empty         + "," +
                "\"maintenance\":" + maintenance   + "," +
                "\"zones\":"       + zonesJson     + "," +
                "\"vehicles\":"    + vehiclesJson  + "," +
                "\"pricing\":"     + pricingJson   + "," +
                "\"recent\":"      + recentJson    +
            "}");

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}