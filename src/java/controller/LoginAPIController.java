/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

import dto.Staff;
import dao.StaffDAO;
import utils.InputValidator; 
import utils.TokenProvider;
import java.io.IOException;
import java.util.Scanner;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

/*
 * LoginApiController — handles POST /api/v1/auth/login
 *
 * Called by login.html when a staff member submits their credentials.
 * Supports two content types so it works with both:
 *   - application/x-www-form-urlencoded (plain HTML form submit)
 *   - application/json (login.html sends JSON via fetch)
 *
 * On success, returns a JSON payload with an access_token that the
 * frontend stores in sessionStorage/localStorage for subsequent requests.
 *
 * Response flow:
 *   400 → invalid/dangerous input characters
 *   401 → wrong password or inactive account
 *   200 → login OK, returns { success, access_token, staff_id, name, role }
 */
@WebServlet(name = "LoginApiController", urlPatterns = {"/api/v1/auth/login"})
public class LoginAPIController extends HttpServlet {
    // One shared StaffService instance per servlet lifetime — holds the token store in memory
    private final StaffDAO staffService = new StaffDAO();
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {               
        request.setCharacterEncoding("UTF-8");
        // Always respond with JSON so the frontend can parse { success, message } uniformly
        response.setContentType("application/json;charset=UTF-8");
        // CORS headers — allow any origin during dev so the frontend can hit this from any port
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        // First try reading as form parameters (form submit / Postman default)
        String name = request.getParameter("name");
        String password = request.getParameter("password");
        if (name == null || password == null) {
            // Parameters not present as form fields — try reading the raw body as JSON instead
            // (login.html sends JSON via fetch with Content-Type: application/json)
            String body;
            try (Scanner scanner = new Scanner(request.getInputStream(), "UTF-8")) {
                body = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            }
            name = extractJsonValue(body, "name");
            password = extractJsonValue(body, "password");
        }
        // Server-side validation gate — mirrors the JS checks in login.html.
        // If this fails, stop immediately with 400 so we never touch the DB with bad input.
        if (name == null || password == null || 
            !InputValidator.isValidLoginInput(name, 100) || 
            !InputValidator.isValidLoginInput(password, 30)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"success\":false,\"message\":\"Input contains invalid characters.\"}");
            return; 
        }
        // Hand off to StaffService to verify credentials against the DB
        Staff authenticatedStaff = staffService.verifyCredentials(name, password);
        if (authenticatedStaff != null) {
            // Login succeeded — generate a fresh token and register it in the in-memory store
            String accessToken = TokenProvider.generateNewToken();
            staffService.saveUserToken(authenticatedStaff.getStaff_id(), accessToken);
            response.setStatus(HttpServletResponse.SC_OK);
            // Build the JSON response manually (no JSON lib dependency needed for this simple shape)
            // Escape any quotes in the staff name to keep the JSON valid
            String jsonResponse = String.format(
                "{\"success\":true,\"access_token\":\"%s\",\"staff_id\":\"%s\",\"name\":\"%s\",\"role\":\"%s\"}",
                accessToken,
                authenticatedStaff.getStaff_id(),
                authenticatedStaff.getName().replace("\"", "\\\""),
                authenticatedStaff.getRole().name().toLowerCase()
            );
            response.getWriter().print(jsonResponse);
        } else {
            // Credentials wrong or account inactive — 401 Unauthorized
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"success\":false,\"message\":\"Invalid credentials.\"}");
        }
    }
    
    // extractJsonValue — tiny manual JSON parser for a single string field
    // Scans for "key": "value" pattern by finding quotes around the value.
    private String extractJsonValue(String json, String key) {
        try {
            int keyIdx = json.indexOf("\"" + key + "\"");
            if (keyIdx == -1) return null;
            int colonIdx = json.indexOf(":", keyIdx);
            int startQuote = json.indexOf("\"", colonIdx);
            int endQuote = json.indexOf("\"", startQuote + 1);
            return json.substring(startQuote + 1, endQuote).trim();
        } catch (Exception e) { 
            return null; 
        }
    }
    
    // doOptions handles CORS preflight requests that browsers send before a cross-origin POST
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}