package dao;

import dto.Booking;
import utils.DbUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/*
 * BookingDAO — the full lifecycle of a parking reservation.
 *
 * Every write operation that touches more than one table runs inside an atomic
 * SQL transaction so the slot status and the booking record never get out of sync.
 */
public class BookingDAO {

    // Creates a new advance booking, locking the slot atomically to prevent double-booking.
    // Returns the generated booking ID on success, or -1 if the chosen slot was already taken.
    public int createAdvanceBooking(Booking booking) throws Exception {
        Connection conn = null;
        PreparedStatement updateStmt = null;
        PreparedStatement insertStmt = null;
        ResultSet generatedKeys = null;
        int bookingId = -1;

        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            // Try to claim the slot — if it isn't 'Empty' anymore, rowsAffected will be 0
            String updateSql = "UPDATE dbo.Parking_slot SET status = 'Reserved' WHERE slot_id = ? AND status = 'Empty'";
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setInt(1, booking.getSlotId());
            int rowsAffected = updateStmt.executeUpdate();

            if (rowsAffected == 1) {
                // Slot is ours — create the booking record with customer and payment info
                String insertSql = "INSERT INTO dbo.Booking (customer_name, customer_phone, license_plate, vehicle_type_id, slot_id, target_time, payment_method, vnpay_txn_ref) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                insertStmt = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS);
                insertStmt.setString(1, booking.getCustomerName());
                insertStmt.setString(2, booking.getCustomerPhone());
                insertStmt.setString(3, booking.getLicensePlate());
                insertStmt.setInt(4, booking.getVehicleTypeId());
                insertStmt.setInt(5, booking.getSlotId());
                insertStmt.setString(6, formatDatetime(booking.getTargetTime()));
                insertStmt.setString(7, booking.getPaymentMethod() != null ? booking.getPaymentMethod() : "Cash");
                insertStmt.setString(8, booking.getVnpayTxnRef()); // null is fine for Cash
                insertStmt.executeUpdate();
                
                generatedKeys = insertStmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    bookingId = generatedKeys.getInt(1);
                }
                conn.commit();
            } else {
                // Slot was taken between the time the customer picked it and now
                conn.rollback();
            }
        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (Exception ignored) {}
            }
            throw e;
        } finally {
            if (generatedKeys != null) generatedKeys.close();
            if (updateStmt != null) updateStmt.close();
            if (insertStmt != null) insertStmt.close();
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
        return bookingId;
    }

    // Finds a booking by its VNPay transaction reference — used on the payment return callback
    // to identify which booking just got paid.
    public Booking findByTxnRef(String txnRef) throws Exception {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            String sql = "SELECT booking_id, slot_id FROM dbo.Booking WHERE vnpay_txn_ref = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, txnRef);
            rs = stmt.executeQuery();
            if (rs.next()) {
                Booking b = new Booking();
                b.setBookingId(rs.getInt("booking_id"));
                b.setSlotId(rs.getInt("slot_id"));
                return b;
            }
            return null;
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }

    // Returns all bookings sorted by how soon the scheduled arrival is.
    // The frontend uses the joined type_name and slot_code for display.
    public List<Booking> getAllBookings() throws Exception {
        List<Booking> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            String sql = "SELECT b.*, v.type_name, s.slot_code, a.area_code " +
                         "FROM dbo.Booking b " +
                         "JOIN dbo.Vehicle_type v ON b.vehicle_type_id = v.vehicle_type_id " +
                         "JOIN dbo.Parking_slot s ON b.slot_id = s.slot_id " +
                         "JOIN dbo.Parking_area a ON s.area_id = a.area_id " +
                         "ORDER BY b.target_time DESC";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            while (rs.next()) {
                Booking b = new Booking();
                b.setBookingId(rs.getInt("booking_id"));
                b.setCustomerName(rs.getString("customer_name"));
                b.setCustomerPhone(rs.getString("customer_phone"));
                b.setLicensePlate(rs.getString("license_plate"));
                b.setVehicleTypeId(rs.getInt("vehicle_type_id"));
                b.setVehicleTypeName(rs.getString("type_name"));
                b.setSlotId(rs.getInt("slot_id"));
                b.setSlotCode(rs.getString("slot_code"));
                b.setAreaCode(rs.getString("area_code"));
                b.setTargetTime(rs.getString("target_time"));
                b.setCreatedAt(rs.getString("created_at"));
                b.setStatus(rs.getString("status"));
                b.setPaymentMethod(rs.getString("payment_method"));
                b.setVnpayTxnRef(rs.getString("vnpay_txn_ref"));
                b.setCancellationReason(rs.getString("cancellation_reason"));
                list.add(b);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
        return list;
    }

    // Looks up a customer's active and past bookings by phone + license plate.
    // This is the lookup used by the customer tracking page — no login required.
    public List<Booking> getBookingsByCustomer(String phone, String licensePlate) throws Exception {
        List<Booking> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            String sql = "SELECT b.booking_id, b.customer_name, b.license_plate, b.target_time, " +
                         "b.created_at, b.status, b.payment_method, b.cancellation_reason, " +
                         "v.type_name, s.slot_code, a.area_code " +
                         "FROM dbo.Booking b " +
                         "JOIN dbo.Vehicle_type v ON b.vehicle_type_id = v.vehicle_type_id " +
                         "JOIN dbo.Parking_slot s ON b.slot_id = s.slot_id " +
                         "JOIN dbo.Parking_area a ON s.area_id = a.area_id " +
                         "WHERE b.customer_phone = ? AND b.license_plate = ? " +
                         "ORDER BY b.target_time DESC";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, phone);
            stmt.setString(2, licensePlate);
            rs = stmt.executeQuery();
            while (rs.next()) {
                Booking b = new Booking();
                b.setBookingId(rs.getInt("booking_id"));
                b.setCustomerName(rs.getString("customer_name"));
                b.setLicensePlate(rs.getString("license_plate"));
                b.setVehicleTypeName(rs.getString("type_name"));
                b.setSlotCode(rs.getString("slot_code"));
                b.setAreaCode(rs.getString("area_code"));
                b.setTargetTime(rs.getString("target_time"));
                b.setCreatedAt(rs.getString("created_at"));
                b.setStatus(rs.getString("status"));
                b.setPaymentMethod(rs.getString("payment_method"));
                b.setCancellationReason(rs.getString("cancellation_reason"));
                list.add(b);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
        return list;
    }

    // Marks the booking as checked-in (occupied) and flips the slot to Occupied.
    // Only works on active bookings — guards against double check-in.
    public boolean checkInBooking(int bookingId) throws Exception {
        Connection conn = null;
        PreparedStatement getStmt = null;
        PreparedStatement updateSlotStmt = null;
        PreparedStatement updateBookingStmt = null;
        boolean success = false;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            String getSql = "SELECT slot_id FROM dbo.Booking WHERE booking_id = ? AND status = 'active'";
            getStmt = conn.prepareStatement(getSql);
            getStmt.setInt(1, bookingId);
            ResultSet rs = getStmt.executeQuery();

            if (rs.next()) {
                int slotId = rs.getInt("slot_id");

                updateSlotStmt = conn.prepareStatement("UPDATE dbo.Parking_slot SET status = 'Occupied' WHERE slot_id = ?");
                updateSlotStmt.setInt(1, slotId);
                updateSlotStmt.executeUpdate();

                updateBookingStmt = conn.prepareStatement("UPDATE dbo.Booking SET status = 'occupied' WHERE booking_id = ?");
                updateBookingStmt.setInt(1, bookingId);
                updateBookingStmt.executeUpdate();

                conn.commit();
                success = true;
            } else {
                conn.rollback();
            }
        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (getStmt != null) getStmt.close();
            if (updateSlotStmt != null) updateSlotStmt.close();
            if (updateBookingStmt != null) updateBookingStmt.close();
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
        return success;
    }

    // Marks an occupied booking as checked-out and flips the slot to Empty.
    public boolean checkoutBooking(int bookingId) throws Exception {
        Connection conn = null;
        PreparedStatement getStmt = null;
        PreparedStatement updateSlotStmt = null;
        PreparedStatement updateBookingStmt = null;
        boolean success = false;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            String getSql = "SELECT slot_id FROM dbo.Booking WHERE booking_id = ? AND status = 'occupied'";
            getStmt = conn.prepareStatement(getSql);
            getStmt.setInt(1, bookingId);
            ResultSet rs = getStmt.executeQuery();

            if (rs.next()) {
                int slotId = rs.getInt("slot_id");

                updateSlotStmt = conn.prepareStatement("UPDATE dbo.Parking_slot SET status = 'Empty' WHERE slot_id = ?");
                updateSlotStmt.setInt(1, slotId);
                updateSlotStmt.executeUpdate();

                updateBookingStmt = conn.prepareStatement("UPDATE dbo.Booking SET status = 'checked-out' WHERE booking_id = ?");
                updateBookingStmt.setInt(1, bookingId);
                updateBookingStmt.executeUpdate();

                conn.commit();
                success = true;
            } else {
                conn.rollback();
            }
        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (getStmt != null) getStmt.close();
            if (updateSlotStmt != null) updateSlotStmt.close();
            if (updateBookingStmt != null) updateBookingStmt.close();
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
        return success;
    }

    // Cancels a booking and returns the slot back to Empty.
    // An optional reason can be stored for the customer to see on their tracking page.
    public boolean cancelBooking(int bookingId, String reason) throws Exception {
        Connection conn = null;
        PreparedStatement getStmt = null;
        PreparedStatement updateSlotStmt = null;
        PreparedStatement updateBookingStmt = null;
        boolean success = false;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            String getSql = "SELECT slot_id FROM dbo.Booking WHERE booking_id = ? AND status = 'active'";
            getStmt = conn.prepareStatement(getSql);
            getStmt.setInt(1, bookingId);
            ResultSet rs = getStmt.executeQuery();

            if (rs.next()) {
                int slotId = rs.getInt("slot_id");

                updateSlotStmt = conn.prepareStatement("UPDATE dbo.Parking_slot SET status = 'Empty' WHERE slot_id = ?");
                updateSlotStmt.setInt(1, slotId);
                updateSlotStmt.executeUpdate();

                updateBookingStmt = conn.prepareStatement("UPDATE dbo.Booking SET status = 'canceled', cancellation_reason = ? WHERE booking_id = ?");
                updateBookingStmt.setString(1, reason);
                updateBookingStmt.setInt(2, bookingId);
                updateBookingStmt.executeUpdate();

                conn.commit();
                success = true;
            } else {
                conn.rollback();
            }
        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (getStmt != null) getStmt.close();
            if (updateSlotStmt != null) updateSlotStmt.close();
            if (updateBookingStmt != null) updateBookingStmt.close();
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
        return success;
    }

    // Hard-deletes a booking from history. Only works on finished bookings
    // so staff can't accidentally delete a live reservation.
    public boolean removeBooking(int bookingId) throws Exception {
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = DbUtils.getConnection();
            String sql = "DELETE FROM dbo.Booking WHERE booking_id = ? AND status IN ('canceled', 'checked-out')";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, bookingId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } finally {
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }

    // Updates the details of an existing booking. Editing changes the slot, so we need
    // to free the old slot and claim the new one inside a single transaction.
    public boolean editBooking(Booking booking) throws Exception {
        Connection conn = null;
        PreparedStatement freeOldStmt = null;
        PreparedStatement claimNewStmt = null;
        PreparedStatement updateBookingStmt = null;
        boolean success = false;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            // Find the current slot so we can release it
            PreparedStatement getStmt = conn.prepareStatement("SELECT slot_id FROM dbo.Booking WHERE booking_id = ?");
            getStmt.setInt(1, booking.getBookingId());
            ResultSet rs = getStmt.executeQuery();
            if (!rs.next()) { conn.rollback(); return false; }
            int oldSlotId = rs.getInt("slot_id");
            getStmt.close();

            // Release the old slot and claim the new one (if it's different)
            if (oldSlotId != booking.getSlotId()) {
                freeOldStmt = conn.prepareStatement("UPDATE dbo.Parking_slot SET status = 'Empty' WHERE slot_id = ?");
                freeOldStmt.setInt(1, oldSlotId);
                freeOldStmt.executeUpdate();

                claimNewStmt = conn.prepareStatement("UPDATE dbo.Parking_slot SET status = 'Reserved' WHERE slot_id = ? AND status = 'Empty'");
                claimNewStmt.setInt(1, booking.getSlotId());
                int claimed = claimNewStmt.executeUpdate();
                if (claimed == 0) { conn.rollback(); return false; } // new slot was taken
            }

            String sql = "UPDATE dbo.Booking SET license_plate = ?, vehicle_type_id = ?, slot_id = ?, target_time = ? WHERE booking_id = ?";
            updateBookingStmt = conn.prepareStatement(sql);
            updateBookingStmt.setString(1, booking.getLicensePlate());
            updateBookingStmt.setInt(2, booking.getVehicleTypeId());
            updateBookingStmt.setInt(3, booking.getSlotId());
            updateBookingStmt.setString(4, formatDatetime(booking.getTargetTime()));
            updateBookingStmt.setInt(5, booking.getBookingId());
            int rows = updateBookingStmt.executeUpdate();
            conn.commit();
            success = rows > 0;
        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (freeOldStmt != null) freeOldStmt.close();
            if (claimNewStmt != null) claimNewStmt.close();
            if (updateBookingStmt != null) updateBookingStmt.close();
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
        return success;
    }

    // Updates the VNPay transaction reference on a booking after we receive the real booking ID.
    // This is called immediately after createAdvanceBooking so the return URL can find the booking.
    public void updateTxnRef(int bookingId, String txnRef) throws Exception {
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = DbUtils.getConnection();
            stmt = conn.prepareStatement("UPDATE dbo.Booking SET vnpay_txn_ref = ? WHERE booking_id = ?");
            stmt.setString(1, txnRef);
            stmt.setInt(2, bookingId);
            stmt.executeUpdate();
        } finally {
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }

    // Returns all slots with their current status plus which zone they belong to.
    // Used by the customer-facing slot map so they can see what's open before picking.
    public List<String[]> getAllSlotsForMap() throws Exception {
        List<String[]> slots = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            String sql = "SELECT s.slot_id, s.slot_code, s.slot_type, s.status, a.area_code " +
                         "FROM dbo.Parking_slot s " +
                         "JOIN dbo.Parking_area a ON s.area_id = a.area_id " +
                         "ORDER BY a.area_code, s.slot_code";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            while (rs.next()) {
                slots.add(new String[]{
                    String.valueOf(rs.getInt("slot_id")),
                    rs.getString("slot_code"),
                    rs.getString("slot_type"),
                    rs.getString("status"),
                    rs.getString("area_code")
                });
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
        return slots;
    }

    // Cleans up the datetime string coming from an HTML datetime-local input field.
    // The browser sends "2026-08-15T10:00" but SQL Server wants "2026-08-15 10:00:00".
    private String formatDatetime(String raw) {
        if (raw == null) return null;
        String result = raw.contains("T") ? raw.replace("T", " ") : raw;
        if (result.length() == 16) result += ":00";
        return result;
    }
}
