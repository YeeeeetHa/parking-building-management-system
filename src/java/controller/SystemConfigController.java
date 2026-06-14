/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.DbUtils;

@WebServlet(name = "SystemConfigController", urlPatterns = {"/api/v1/admin/config"})
public class SystemConfigController extends HttpServlet {

    /**
     * GET: Pulls all current configuration parameters from SQL Server and maps them into a clean JSON object payload.
     */
    /**
     * GET: Pulls all current configuration parameters from SQL Server and maps them into a JSON object.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        
        StringBuilder json = new StringBuilder("{");
        String sql = "SELECT config_key, config_value FROM SystemConfig";
        
        // Try-with-resources isolates JDBC objects to manage connection tracking cleanly
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append("\"").append(rs.getString("config_key")).append("\":")
                    .append("\"").append(rs.getString("config_value")).append("\"");
                first = false;
            }
            
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.append("\"error\":\"Database fetch execution failure.\"");
            e.printStackTrace();
        }
        
        json.append("}");
        try (PrintWriter out = response.getWriter()) {
            out.print(json.toString());
        }
    }

    /**
     * POST: Updates a specific operational parameter row and resets database field parameters instantly.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String key = request.getParameter("key");
        String value = request.getParameter("value");
        
        if (key == null || value == null || key.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String sql = "UPDATE SystemConfig SET config_value = ?, updated_at = SYSDATETIME() WHERE config_key = ?";
        
        // Connection pool managed resources - auto-closed explicitly upon block exit to pass connection leak audits
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, value.trim());
            ps.setString(2, key.trim());
            
            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated == 0) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
            }
            
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            e.printStackTrace();
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(SystemConfigController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}