package controller;

import dao.BookingDAO;
import dao.StaffDAO;
import dto.Booking;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/*
 * BookingAPIController — handles POST /api/v1/bookings/create
 *
 * This is the backend entry point for the advance slot reservation form.
 * The frontend (dashboard.html) submits a form POST here with four fields: licensePlate, vehicleType, slotId, targetTime.
 *
 * Response contract (plain text, not JSON):
 *   201 Created  → "Booking created successfully"
 *   409 Conflict → "Slot is already booked or unavailable"
 *   400 Bad Request → "Invalid license plate format" or "Invalid numeric parameters"
 *   401 Unauthorized → "Missing or invalid Authorization header"
 *   500          → wrapped ServletException
 */
@WebServlet(name = "BookingAPIController", urlPatterns = {"/api/v1/bookings/create"})
public class BookingAPIController extends HttpServlet {
    
    // doPost is triggered when the advance booking form hits Confirm Reservation
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
            
        // 1. Authorize the incoming request using standard Bearer token sweeps
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing or invalid Authorization header");
            return;
        }
        
        // Strip out the "Bearer " prefix to extract the raw token
        String token = authHeader.substring(7);
        StaffDAO staffDao = new StaffDAO();
        if (!staffDao.isValidToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or expired session token");
            return;
        }

        // 2. Pull each form field out of the payload dispatcher
        String licensePlate = request.getParameter("licensePlate");
        String vehicleTypeStr = request.getParameter("vehicleType");
        String slotIdStr = request.getParameter("slotId");
        String targetTime = request.getParameter("targetTime");

        // Strict client-side-matching validation loop to sanitize input characters
        if (licensePlate == null || !licensePlate.matches("^[A-Za-z0-9- ]+$")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid license plate format");
            return;
        }
        
        int vehicleType, slotId;
        try {
            vehicleType = Integer.parseInt(vehicleTypeStr);
            slotId = Integer.parseInt(slotIdStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid numeric parameters");
            return;
        }

        // Pack everything into a Booking DTO for a clean separation of concerns
        Booking booking = new Booking();
        booking.setLicensePlate(licensePlate);
        booking.setVehicleTypeId(vehicleType);
        booking.setSlotId(slotId);
        booking.setTargetTime(targetTime);

        // 3. Hand off to the DAO layer to process the transaction
        BookingDAO dao = new BookingDAO();
        try {
            boolean success = dao.createAdvanceBooking(booking);
            if (success) {
                // 201 = "resource created" — standard REST response for successful insertions
                response.setStatus(HttpServletResponse.SC_CREATED);
                response.getWriter().write("Booking created successfully");
            } else {
                // 409 = "conflict" — slot was occupied or unavailable during atomic lock
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("Slot is already booked or unavailable");
            }
        } catch (SQLException ex) {
            // Any SQL error bubbles up as a server error 500
            throw new ServletException("Database error during booking", ex);
        }
    }
}
