/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

import utils.DbUtils;
import utils.InputValidator;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name = "ForgotPasswordApiController", urlPatterns = {"/api/v1/auth/forgot-password"})
public class ForgotPasswordApiController extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        
        // Cấu hình CORS để phục vụ môi trường Dev
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        String method = request.getParameter("method"); // "email" hoặc "phone"
        String contactInfo = request.getParameter("contactInfo");

        // Sử dụng InputValidator có sẵn của bạn để làm sạch dữ liệu đầu vào
        if (contactInfo == null || !InputValidator.isValidLoginInput(contactInfo, 100)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"success\":false,\"message\":\"Dữ liệu đầu vào không hợp lệ.\"}");
            return;
        }

        contactInfo = contactInfo.trim();
        String sql = "";
        
        // Xác định câu lệnh SQL dựa trên phương thức nhận mã
        if ("email".equalsIgnoreCase(method)) {
            sql = "SELECT staff_id, name FROM dbo.Staff WHERE email = ? AND status = 'active'";
        } else if ("phone".equalsIgnoreCase(method)) {
            sql = "SELECT staff_id, name FROM dbo.Staff WHERE phone = ? AND status = 'active'";
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"success\":false,\"message\":\"Phương thức không hợp lệ.\"}");
            return;
        }

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, contactInfo);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int staffId = rs.getInt("staff_id");
                    String staffName = rs.getString("name");

                    // 1. Khởi tạo mã bí mật ngẫu nhiên mã hóa OTP (6 số)
                    String otpCode = String.format("%06d", new Random().nextInt(999999));
                    
                    // 2. Logic gửi mã thực tế
                    if ("email".equalsIgnoreCase(method)) {
                        // Gọi hàm Email Service (Ví dụ: JavaMail API) gửi đến `contactInfo`
                        System.out.println("LOG SYSTEM: Đã gửi OTP [" + otpCode + "] đến Email: " + contactInfo);
                    } else {
                        // Gọi SMS Gateway API gửi đến số điện thoại `contactInfo`
                        System.out.println("LOG SYSTEM: Đã gửi OTP [" + otpCode + "] đến SMS SĐT: " + contactInfo);
                    }

                    // 3. Trả về thông báo thành công cho Client
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().print("{\"success\":true,\"message\":\"Mã xác thực OTP đã được gửi đi thành công!\"}");
                    
                } else {
                    // Trả về lỗi 404 nếu không tìm thấy thông tin nhân viên nào khớp
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().print("{\"success\":false,\"message\":\"Thông tin tài khoản không tồn tại trên hệ thống.\"}");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"success\":false,\"message\":\"Lỗi hệ thống cơ sở dữ liệu!\"}");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}