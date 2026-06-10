package controller;

import dao.BookingDAO;
import dao.StaffDAO;
import dto.Booking;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/*
 * BookingAPIController — handles /api/v1/bookings/*
 *
 * This controller now routes multiple booking lifecycle endpoints:
 * - /create (POST)
 * - /list (GET)
 * - /checkin (POST)
 * - /cancel (DELETE)
 * - /remove (DELETE)
 */
@WebServlet(name = "BookingAPIController", urlPatterns = {"/api/v1/bookings/*"})
public class BookingAPIController extends HttpServlet {

    // Ensures all endpoints are protected by Bearer token sweeps
    private boolean authenticate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing or invalid Authorization header");
            return false;
        }
        String token = authHeader.substring(7);
        StaffDAO staffDao = new StaffDAO();
        if (!staffDao.isValidToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or expired session token");
            return false;
        }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!authenticate(request, response)) return;
        
        String pathInfo = request.getPathInfo();
        if ("/list".equals(pathInfo)) {
            handleList(response);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!authenticate(request, response)) return;

        String pathInfo = request.getPathInfo();
        if ("/create".equals(pathInfo)) {
            handleCreate(request, response);
        } else if ("/checkin".equals(pathInfo)) {
            handleCheckIn(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!authenticate(request, response)) return;

        String pathInfo = request.getPathInfo();
        if ("/cancel".equals(pathInfo)) {
            handleCancel(request, response);
        } else if ("/remove".equals(pathInfo)) {
            handleRemove(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!authenticate(request, response)) return;

        String pathInfo = request.getPathInfo();
        if ("/edit".equals(pathInfo)) {
            handleEdit(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleList(HttpServletResponse response) throws IOException, ServletException {
        BookingDAO dao = new BookingDAO();
        try {
            List<Booking> list = dao.getAllBookings();
            
            // Build simple JSON array manually to avoid external dependencies
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                Booking b = list.get(i);
                json.append("{");
                json.append("\"bookingId\":").append(b.getBookingId()).append(",");
                json.append("\"licensePlate\":\"").append(b.getLicensePlate()).append("\",");
                json.append("\"vehicleTypeName\":\"").append(b.getVehicleTypeName()).append("\",");
                json.append("\"slotCode\":\"").append(b.getSlotCode()).append("\",");
                json.append("\"targetTime\":\"").append(b.getTargetTime()).append("\",");
                json.append("\"status\":\"").append(b.getStatus()).append("\"");
                json.append("}");
                if (i < list.size() - 1) json.append(",");
            }
            json.append("]");

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json.toString());
            
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }

    private void handleCreate(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String licensePlate = request.getParameter("licensePlate");
        String vehicleTypeStr = request.getParameter("vehicleType");
        String slotIdStr = request.getParameter("slotId");
        String targetTime = request.getParameter("targetTime");

        if (licensePlate == null || !licensePlate.matches("^[A-Za-z0-9\\.\\-\\ ]+$")) {
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

        Booking booking = new Booking();
        booking.setLicensePlate(licensePlate);
        booking.setVehicleTypeId(vehicleType);
        booking.setSlotId(slotId);
        booking.setTargetTime(targetTime);

        BookingDAO dao = new BookingDAO();
        try {
            boolean success = dao.createAdvanceBooking(booking);
            if (success) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                response.getWriter().write("Booking created successfully");
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("Slot is already booked or unavailable");
            }
        } catch (SQLException ex) {
            throw new ServletException("Database error during booking", ex);
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }

    private void handleCheckIn(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String bookingIdStr = request.getParameter("bookingId");
        try {
            int bookingId = Integer.parseInt(bookingIdStr);
            BookingDAO dao = new BookingDAO();
            if (dao.checkInBooking(bookingId)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("Booking checked in successfully");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Unable to check in booking");
            }
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw new ServletException(ex);
        }
    }

    private void handleCancel(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String bookingIdStr = request.getParameter("bookingId");
        try {
            int bookingId = Integer.parseInt(bookingIdStr);
            BookingDAO dao = new BookingDAO();
            if (dao.cancelBooking(bookingId)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("Booking canceled successfully");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Unable to cancel booking");
            }
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw new ServletException(ex);
        }
    }

    private void handleRemove(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String bookingIdStr = request.getParameter("bookingId");
        try {
            int bookingId = Integer.parseInt(bookingIdStr);
            BookingDAO dao = new BookingDAO();
            if (dao.removeBooking(bookingId)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("Booking removed successfully");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Unable to remove booking");
            }
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw new ServletException(ex);
        }
    }

    private void handleEdit(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String bookingIdStr = request.getParameter("bookingId");
        String licensePlate = request.getParameter("licensePlate");
        String vehicleTypeStr = request.getParameter("vehicleType");
        String slotIdStr = request.getParameter("slotId");
        String targetTime = request.getParameter("targetTime");

        if (licensePlate == null || !licensePlate.matches("^[A-Za-z0-9\\.\\-\\ ]+$")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid license plate format");
            return;
        }

        int bookingId, vehicleType, slotId;
        try {
            bookingId = Integer.parseInt(bookingIdStr);
            vehicleType = Integer.parseInt(vehicleTypeStr);
            slotId = Integer.parseInt(slotIdStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid numeric parameters");
            return;
        }

        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setLicensePlate(licensePlate);
        booking.setVehicleTypeId(vehicleType);
        booking.setSlotId(slotId);
        booking.setTargetTime(targetTime);

        BookingDAO dao = new BookingDAO();
        try {
            if (dao.editBooking(booking)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("Booking updated successfully");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Unable to update booking");
            }
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw new ServletException(ex);
        }
    }
}
