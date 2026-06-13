package controller;

import dao.BookingDAO;
import dto.Booking;
import utils.VNPayService;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/*
 * PaymentController — handles the two-step VNPay payment process for customer bookings.
 *
 * /api/v1/payment/create  — receives booking details from the customer book page,
 *                           creates a pending booking record, then redirects the browser
 *                           to the VNPay sandbox payment gateway.
 *
 * /api/v1/payment/return  — VNPay sends the customer here after they finish (or cancel) payment.
 *                           We verify the hash, confirm the result, and redirect the customer
 *                           to either a success page or a failure page.
 */
@WebServlet(name = "PaymentController", urlPatterns = {"/api/v1/payment/*"})
public class PaymentController extends HttpServlet {

    // Calculates a simple booking fee based on vehicle type pricing (first hour only for now).
    // In the real system this would come from Pricing_rules. For this sprint, flat rates are used.
    private long calculateAmount(int vehicleTypeId) {
        switch (vehicleTypeId) {
            case 1: return 15000;  // Sedan
            case 2: return 20000;  // SUV / Truck
            case 3: return 5000;   // Motorbike
            default: return 10000;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();

        if ("/create".equals(pathInfo)) {
            handleCreate(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();

        if ("/return".equals(pathInfo)) {
            handleReturn(request, response);
        } else if ("/slots".equals(pathInfo)) {
            handleGetSlots(response);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    // Receives the booking form submission, creates a tentative booking, then
    // redirects to VNPay so the customer can pay online.
    private void handleCreate(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String customerName  = request.getParameter("customerName");
        String customerPhone = request.getParameter("customerPhone");
        String licensePlate  = request.getParameter("licensePlate");
        String vehicleTypeStr = request.getParameter("vehicleType");
        String slotIdStr     = request.getParameter("slotId");
        String targetTime    = request.getParameter("targetTime");
        String paymentMethod = request.getParameter("paymentMethod");

        // Basic validation before we even touch the database
        if (customerName == null || customerName.trim().isEmpty() ||
            customerPhone == null || licensePlate == null ||
            !licensePlate.matches("^[A-Za-z0-9\\.\\-\\ ]+$")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Missing or invalid fields.\"}");
            return;
        }

        int vehicleType, slotId;
        try {
            vehicleType = Integer.parseInt(vehicleTypeStr);
            slotId      = Integer.parseInt(slotIdStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid slot or vehicle type.\"}");
            return;
        }

        Booking booking = new Booking();
        booking.setCustomerName(customerName.trim());
        booking.setCustomerPhone(customerPhone.trim());
        booking.setLicensePlate(licensePlate.trim());
        booking.setVehicleTypeId(vehicleType);
        booking.setSlotId(slotId);
        booking.setTargetTime(targetTime);
        booking.setPaymentMethod(paymentMethod != null ? paymentMethod : "Cash");

        BookingDAO dao = new BookingDAO();

        try {
            if ("Cash".equalsIgnoreCase(paymentMethod)) {
                // Cash booking — finalize immediately and redirect to confirmation
                int newBookingId = dao.createAdvanceBooking(booking);
                if (newBookingId > 0) {
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":true,\"paymentMethod\":\"Cash\"}");
                } else {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    response.getWriter().write("{\"success\":false,\"message\":\"That slot was just taken. Please pick another one.\"}");
                }
            } else {
                // VNPay — we first reserve the slot with a temporary txnRef, then redirect to VNPay
                // The txnRef is a placeholder before we have a real booking_id
                String tempRef = "TEMP-" + System.currentTimeMillis();
                booking.setVnpayTxnRef(tempRef);

                int newBookingId = dao.createAdvanceBooking(booking);
                if (newBookingId <= 0) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    response.getWriter().write("{\"success\":false,\"message\":\"That slot was just taken. Please pick another one.\"}");
                    return;
                }

                // Generate a proper txnRef with the real booking ID
                String txnRef = VNPayService.generateTxnRef(newBookingId);

                // Update the booking with the real txnRef so the return callback can find it
                dao.updateTxnRef(newBookingId, txnRef);

                // Build the return URL — this is where VNPay sends the customer back to
                String scheme = request.getScheme();
                String serverName = request.getServerName();
                int port = request.getServerPort();
                String contextPath = request.getContextPath();
                String returnUrl = scheme + "://" + serverName + ":" + port + contextPath + "/api/v1/payment/return";

                long amount = calculateAmount(vehicleType);
                String orderInfo = "SmartParking - Slot " + slotId + " - " + licensePlate;
                String clientIp = request.getRemoteAddr();

                String paymentUrl = VNPayService.buildPaymentUrl(amount, txnRef, orderInfo, returnUrl, clientIp);

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"success\":true,\"paymentMethod\":\"VNPay\",\"paymentUrl\":\"" + paymentUrl + "\"}");
            }
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }

    // VNPay redirects the customer's browser to this endpoint after payment.
    // We verify the hash to confirm VNPay sent this (not someone who guessed the URL),
    // then redirect the customer to the customer portal's result page.
    private void handleReturn(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            // Pull all query params from the VNPay redirect into a map for hash verification
            java.util.Map<String, String> params = new java.util.HashMap<>();
            request.getParameterMap().forEach((key, values) -> {
                if (values.length > 0) params.put(key, values[0]);
            });

            boolean hashValid = VNPayService.verifyReturnHash(params);
            String responseCode = params.get("vnp_ResponseCode");
            String txnRef = params.get("vnp_TxnRef");

            String contextPath = request.getContextPath();

            if (hashValid && "00".equals(responseCode) && txnRef != null) {
                // Payment successful — redirect to the customer confirmation page
                response.sendRedirect(contextPath + "/customer/book.html?result=success&ref=" + txnRef);
            } else {
                // Payment failed or cancelled — redirect with error so the page can show a message
                // We also need to cancel the booking so the slot opens back up
                if (txnRef != null) {
                    BookingDAO dao = new BookingDAO();
                    Booking b = dao.findByTxnRef(txnRef);
                    if (b != null) {
                        dao.cancelBooking(b.getBookingId(), "Payment not completed.");
                    }
                }
                response.sendRedirect(contextPath + "/customer/book.html?result=failed");
            }
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }

    // Returns all parking slots with their current status for the visual slot map.
    // No authentication needed — the map is public so customers can pick a slot.
    private void handleGetSlots(HttpServletResponse response) throws IOException, ServletException {
        try {
            BookingDAO dao = new BookingDAO();
            List<String[]> slots = dao.getAllSlotsForMap();

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < slots.size(); i++) {
                String[] s = slots.get(i);
                json.append("{")
                    .append("\"slotId\":").append(s[0]).append(",")
                    .append("\"slotCode\":\"").append(s[1]).append("\",")
                    .append("\"slotType\":\"").append(s[2]).append("\",")
                    .append("\"status\":\"").append(s[3]).append("\",")
                    .append("\"areaCode\":\"").append(s[4]).append("\"")
                    .append("}");
                if (i < slots.size() - 1) json.append(",");
            }
            json.append("]");

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json.toString());
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }
}
