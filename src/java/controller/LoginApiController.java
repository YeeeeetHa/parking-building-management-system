/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

import dto.Staff;
import dao.StaffService;
import utils.InputValidator; // Import our new validator class
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet(name = "LoginApiController", urlPatterns = {"/api/v1/auth/login"})
public class LoginApiController extends HttpServlet {
    private final StaffService staffService = new StaffService();
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {       
        response.setContentType("application/json;charset=UTF-8");
        String name = request.getParameter("name");
        String password = request.getParameter("password");
        if (name == null || password == null) {
            java.util.Scanner scanner = new java.util.Scanner(request.getInputStream(), "UTF-8");
            String body = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            scanner.close();

            name = body.contains("name=") ? extractFormValue(body, "name") : extractJsonValue(body, "name");
            password = body.contains("password=") ? extractFormValue(body, "password") : extractJsonValue(body, "password");
        }

        if (!InputValidator.isValidLoginInput(name, 100) || !InputValidator.isValidLoginInput(password, 30)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"success\":false,\"message\":\"Invalid or dangerous input characters detected.\"}");
            return; 
        }
        Staff authenticatedStaff = staffService.verifyCredentials(name, password);

        if (authenticatedStaff != null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().print("{\"success\":true}");
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"success\":false,\"message\":\"Invalid credentials.\"}");
        }
    }
    private String extractFormValue(String body, String key) {
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length > 1 && parts[0].trim().equals(key)) {
                try { return java.net.URLDecoder.decode(parts[1], "UTF-8").trim(); } catch(Exception e){}
            }
        }
        return null;
    }
    private String extractJsonValue(String json, String key) {
        try {
            int keyIdx = json.indexOf("\"" + key + "\"");
            int colonIdx = json.indexOf(":", keyIdx);
            int startQuote = json.indexOf("\"", colonIdx);
            int endQuote = json.indexOf("\"", startQuote + 1);
            return json.substring(startQuote + 1, endQuote).trim();
        } catch (Exception e) { return null; }
    }
}