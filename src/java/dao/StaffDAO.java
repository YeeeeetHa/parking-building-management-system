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
import java.util.concurrent.ConcurrentHashMap;

/*
 * StaffService — handles staff authentication and in-memory session token management
 *
 * Two responsibilities:
 *   1. verifyCredentials() — checks username + plain-text password against the DB
 *   2. Token store methods — keep track of active session tokens in a thread-safe map
 *
 * Note: tokens are stored in memory only (ConcurrentHashMap), so they're lost on server restart.
 */
public class StaffDAO {
    // In-memory store: token string → staff_id
    // ConcurrentHashMap is used because multiple requests can read/write tokens concurrently
    private static final ConcurrentHashMap<String, String> activeTokens = new ConcurrentHashMap<>();
    
    /*
     * verifyCredentials — the core login check
     * Looks up the staff by name, then compares the plain-text passwords directly.
     * Returns a fully-populated Staff object on success, or null on failure/inactive.
     */
   public Staff verifyCredentials(String inputName, String inputPlainPassword) {
    if (inputName == null || inputPlainPassword == null) return null;
    // Only fetch the columns we actually need to avoid pulling unnecessary data
    String sql = "SELECT staff_id, password, role, status FROM dbo.Staff WHERE name = ?";
    try (Connection conn = DbUtils.getConnection(); 
         PreparedStatement stmt = conn.prepareStatement(sql)) {       
        stmt.setString(1, inputName.trim());
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                String storedPassword = rs.getString("password");
                String status = rs.getString("status");
                // Trim stored values to be safe — DB might have trailing whitespace (VARCHAR columns)
                if (storedPassword != null) storedPassword = storedPassword.trim();
                if (status != null) status = status.trim();
                // Two-part gate: password must match AND account must be active
                if (inputPlainPassword.trim().equals(storedPassword) && "active".equalsIgnoreCase(status)) {
                    Staff staff = new Staff();
                    staff.setStaff_id(String.valueOf(rs.getInt("staff_id")));
                    staff.setName(inputName.trim());
                    String dbRole = rs.getString("role");
                    if (dbRole != null) {
                        try {
                            // Try uppercase first (e.g. "ADMIN" → StaffRole.admin)
                            staff.setRole(StaffRole.valueOf(dbRole.toUpperCase().trim()));
                        } catch (IllegalArgumentException e) {
                            // Fall back to lowercase if the enum constant is stored lower-case
                            staff.setRole(StaffRole.valueOf(dbRole.toLowerCase().trim()));
                        }
                    }                   
                    staff.setStatus(status);
                    return staff;
                }
            }
        }
    } catch (Exception e) {
        System.err.println("Database Execution Mismatch Error: " + e.getMessage());
        e.printStackTrace();
    }
    // Reaching here means either: wrong password, inactive account, or a DB error
    return null;
}
    // saveUserToken — stores the generated token after login so future requests can validate it
    public void saveUserToken(String staffId, String token) {
        if (staffId != null && token != null) {
            activeTokens.put(token, staffId);
        }
    }
    // isValidToken — quick check used by protected API endpoints before processing a request
    public boolean isValidToken(String token) {
        return token != null && activeTokens.containsKey(token);
    }
    // getStaffIdByToken — lets you look up which staff member owns a given token
    public String getStaffIdByToken(String token) {
        return token != null ? activeTokens.get(token) : null;
    }
    // invalidateToken — called on logout to kick the token out of the active session store
    public void invalidateToken(String token) {
        if (token != null) activeTokens.remove(token);
    }
}