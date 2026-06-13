package controller;

import dao.BookingDAO;
import dao.StaffDAO;
import dto.Booking;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/*
 * BookingAPIController — handles /api/v1/bookings/*
 *
 * All endpoints here are for the staff dashboard. Every request must carry a valid
 * Bearer token in the Authorization header or it will get a 401 back.
 *
 * Endpoints:
 *   GET  /list    — fetch all bookings for the ledger table
 *   GET  /track   — public endpoint, no auth required — customer tracking by phone + plate
 *   POST /checkin — mark a booking as checked-in (slot becomes Occupied)
 *   DELETE /cancel — cancel a booking with an optional reason (slot goes back to Empty)
 *   DELETE /remove — permanently delete a finished/cancelled booking from history
 *   PUT  /edit    — update booking details
 */
@WebServlet(name = "BookingAPIController", urlPatterns = {"/api/v1/bookings/*"})
public class BookingAPIController extends HttpServlet {

    // Checks the Authorization header and validates the token against the in-memory session store
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
        String pathInfo = request.getPathInfo();

        // /track is public — customers use it to look up their own bookings
        if ("/track".equals(pathInfo)) {
            handleTrack(request, response);
            return;
        }

        if (!authenticate(request, response)) return;

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
        if ("/checkin".equals(pathInfo)) {
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

    // Returns all bookings as a JSON array for the staff ledger table
    private void handleList(HttpServletResponse response) throws IOException, ServletException {
        BookingDAO dao = new BookingDAO();
        try {
            List<Booking> list = dao.getAllBookings();

            // Building JSON manually to stay dependency-free
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                Booking b = list.get(i);
                json.append("{");
                json.append("\"bookingId\":").append(b.getBookingId()).append(",");
                json.append("\"customerName\":\"").append(escapeJson(b.getCustomerName())).append("\",");
                json.append("\"customerPhone\":\"").append(escapeJson(b.getCustomerPhone())).append("\",");
                json.append("\"licensePlate\":\"").append(escapeJson(b.getLicensePlate())).append("\",");
                json.append("\"vehicleTypeName\":\"").append(escapeJson(b.getVehicleTypeName())).append("\",");
                json.append("\"slotCode\":\"").append(escapeJson(b.getSlotCode())).append("\",");
                json.append("\"areaCode\":\"").append(escapeJson(b.getAreaCode())).append("\",");
                json.append("\"targetTime\":\"").append(escapeJson(b.getTargetTime())).append("\",");
                json.append("\"status\":\"").append(escapeJson(b.getStatus())).append("\",");
                json.append("\"paymentMethod\":\"").append(escapeJson(b.getPaymentMethod())).append("\",");
                json.append("\"cancellationReason\":\"").append(b.getCancellationReason() != null ? escapeJson(b.getCancellationReason()) : "").append("\"");
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

    // Public endpoint — no auth. Lets a customer look up their bookings with phone + license plate.
    private void handleTrack(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String phone = request.getParameter("phone");
        String plate = request.getParameter("licensePlate");

        if (phone == null || phone.trim().isEmpty() || plate == null || plate.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Phone and license plate are required.\"}");
            return;
        }

        try {
            BookingDAO dao = new BookingDAO();
            List<Booking> list = dao.getBookingsByCustomer(phone.trim(), plate.trim());

            StringBuilder json = new StringBuilder("{\"success\":true,\"bookings\":[");
            for (int i = 0; i < list.size(); i++) {
                Booking b = list.get(i);
                json.append("{");
                json.append("\"bookingId\":").append(b.getBookingId()).append(",");
                json.append("\"customerName\":\"").append(escapeJson(b.getCustomerName())).append("\",");
                json.append("\"licensePlate\":\"").append(escapeJson(b.getLicensePlate())).append("\",");
                json.append("\"vehicleTypeName\":\"").append(escapeJson(b.getVehicleTypeName())).append("\",");
                json.append("\"slotCode\":\"").append(escapeJson(b.getSlotCode())).append("\",");
                json.append("\"areaCode\":\"").append(escapeJson(b.getAreaCode())).append("\",");
                json.append("\"targetTime\":\"").append(escapeJson(b.getTargetTime())).append("\",");
                json.append("\"createdAt\":\"").append(escapeJson(b.getCreatedAt())).append("\",");
                json.append("\"status\":\"").append(escapeJson(b.getStatus())).append("\",");
                json.append("\"paymentMethod\":\"").append(escapeJson(b.getPaymentMethod())).append("\",");
                json.append("\"cancellationReason\":\"").append(b.getCancellationReason() != null ? escapeJson(b.getCancellationReason()) : "").append("\"");
                json.append("}");
                if (i < list.size() - 1) json.append(",");
            }
            json.append("]}");

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json.toString());

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
                response.getWriter().write("Checked in successfully");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Unable to check in — booking may already be checked in or cancelled");
            }
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw new ServletException(ex);
        }
    }

    // Cancel takes an optional 'reason' parameter so staff can explain the cancellation.
    // Customers will see this reason on their tracking page.
    private void handleCancel(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String bookingIdStr = request.getParameter("bookingId");
        String reason = request.getParameter("reason");
        try {
            int bookingId = Integer.parseInt(bookingIdStr);
            BookingDAO dao = new BookingDAO();
            if (dao.cancelBooking(bookingId, reason)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("Booking cancelled");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Unable to cancel — booking may already be cancelled or checked in");
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
                response.getWriter().write("Booking removed from history");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Unable to remove — only finished or cancelled bookings can be removed");
            }
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw new ServletException(ex);
        }
    }

    private void handleEdit(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String bookingIdStr  = request.getParameter("bookingId");
        String licensePlate  = request.getParameter("licensePlate");
        String vehicleTypeStr = request.getParameter("vehicleType");
        String slotIdStr     = request.getParameter("slotId");
        String targetTime    = request.getParameter("targetTime");

        if (bookingIdStr == null || licensePlate == null || vehicleTypeStr == null || slotIdStr == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Missing required parameters");
            return;
        }

        if (!licensePlate.matches("^[A-Za-z0-9\\.\\-\\ ]+$")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid license plate format");
            return;
        }

        int bookingId, vehicleType, slotId;
        try {
            bookingId   = Integer.parseInt(bookingIdStr);
            vehicleType = Integer.parseInt(vehicleTypeStr);
            slotId      = Integer.parseInt(slotIdStr);
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
                response.getWriter().write("Booking updated");
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("Unable to update — the new slot may already be taken");
            }
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw new ServletException(ex);
        }
    }

    // Escapes special characters in strings going into JSON to prevent malformed output
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
