/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import dto.Staff;
import utils.StaffRole;
import utils.DbUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class StaffService {

    public Staff verifyCredentials(String inputName, String inputPlainPassword) {
        if (inputName == null || inputPlainPassword == null) return null;
        
        String sql = "SELECT staff_id, password, role, status FROM dbo.Staff WHERE name = ?";

        try (Connection conn = DbUtils.getConnection(); 
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, inputName.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    String status = rs.getString("status");

                    // Straightforward plain text matching ('123' equals '123')
                    if (inputPlainPassword.trim().equals(storedPassword.trim()) && "active".equalsIgnoreCase(status.trim())) {
                        Staff staff = new Staff();
                        staff.setStaff_id(String.valueOf(rs.getInt("staff_id")));
                        staff.setName(inputName);
                        staff.setRole(StaffRole.valueOf(rs.getString("role").toLowerCase().trim()));
                        staff.setStatus(status);
                        return staff;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}