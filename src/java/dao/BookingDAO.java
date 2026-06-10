package dao;

import dto.Booking;
import utils.DbUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/*
 * BookingDAO — handles advance slot reservation logic
 *
 * It uses a single atomic SQL transaction to prevent double-booking race conditions.
 * The slot status is updated to 'Reserved' and the booking record is created in one step.
 */
public class BookingDAO {
    
    // Executes the booking creation process using an atomic database transaction
    public boolean createAdvanceBooking(Booking booking) throws Exception {
        Connection conn = null;
        PreparedStatement updateStmt = null;
        PreparedStatement insertStmt = null;
        boolean success = false;
        
        try {
            conn = DbUtils.getConnection();
            // Start manual transaction block to ensure both operations succeed or fail together
            conn.setAutoCommit(false);
            // 1. Update slot status atomically
            // This ensures no two transactions can claim the same slot simultaneously
            String updateSql = "UPDATE dbo.Parking_slot SET status = 'Reserved' WHERE slot_id = ? AND status = 'Empty'";
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setInt(1, booking.getSlotId());
            int rowsAffected = updateStmt.executeUpdate();
            // 2. Evaluate rows affected to confirm successful lock
            if (rowsAffected == 1) {
                // The slot was successfully locked, proceed with creating the booking record
                String insertSql = "INSERT INTO dbo.Booking (license_plate, vehicle_type_id, slot_id, target_time) VALUES (?, ?, ?, ?)";
                insertStmt = conn.prepareStatement(insertSql);
                insertStmt.setString(1, booking.getLicensePlate());
                insertStmt.setInt(2, booking.getVehicleTypeId());
                insertStmt.setInt(3, booking.getSlotId());
                // Format the timestamp cleanly for SQL insertion
                String formattedTime = booking.getTargetTime();
                if (formattedTime.contains("T")) {
                    formattedTime = formattedTime.replace("T", " ");
                    if (formattedTime.length() == 16) {
                        formattedTime += ":00";
                    }
                }
                insertStmt.setString(4, formattedTime);
                insertStmt.executeUpdate();
                // Commit the transaction to finalize changes
                conn.commit();
                success = true;
            } else {
                // The slot was not 'Empty', rollback to prevent any partial data creation
                conn.rollback();
                success = false;
            }
        } catch (Exception e) {
            // Revert changes if any database error occurs during the transaction
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception ex) {
                }
            }
            throw e;
        } finally {
            // Clean up database resources
            if (updateStmt != null) updateStmt.close();
            if (insertStmt != null) insertStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
        return success;
    }

    // Fetches all active and completed bookings, joining necessary names, sorted by target time.
    public java.util.List<Booking> getAllBookings() throws Exception {
        java.util.List<Booking> list = new java.util.ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        java.sql.ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            String sql = "SELECT b.*, v.type_name, s.slot_code " +
                         "FROM dbo.Booking b " +
                         "JOIN dbo.Vehicle_type v ON b.vehicle_type_id = v.vehicle_type_id " +
                         "JOIN dbo.Parking_slot s ON b.slot_id = s.slot_id " +
                         "ORDER BY b.target_time DESC";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            while (rs.next()) {
                Booking b = new Booking();
                b.setBookingId(rs.getInt("booking_id"));
                b.setLicensePlate(rs.getString("license_plate"));
                b.setVehicleTypeId(rs.getInt("vehicle_type_id"));
                b.setVehicleTypeName(rs.getString("type_name"));
                b.setSlotId(rs.getInt("slot_id"));
                b.setSlotCode(rs.getString("slot_code"));
                b.setTargetTime(rs.getString("target_time"));
                b.setCreatedAt(rs.getString("created_at"));
                b.setStatus(rs.getString("status"));
                list.add(b);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
        return list;
    }

    // Completes the check-in process: marks booking as completed and slot as Occupied.
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
            java.sql.ResultSet rs = getStmt.executeQuery();
            
            if (rs.next()) {
                int slotId = rs.getInt("slot_id");
                
                String updSlot = "UPDATE dbo.Parking_slot SET status = 'Occupied' WHERE slot_id = ?";
                updateSlotStmt = conn.prepareStatement(updSlot);
                updateSlotStmt.setInt(1, slotId);
                updateSlotStmt.executeUpdate();

                String updBook = "UPDATE dbo.Booking SET status = 'completed' WHERE booking_id = ?";
                updateBookingStmt = conn.prepareStatement(updBook);
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
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
        return success;
    }

    // Cancels a booking and frees the slot.
    public boolean cancelBooking(int bookingId) throws Exception {
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
            java.sql.ResultSet rs = getStmt.executeQuery();
            
            if (rs.next()) {
                int slotId = rs.getInt("slot_id");
                
                String updSlot = "UPDATE dbo.Parking_slot SET status = 'Empty' WHERE slot_id = ?";
                updateSlotStmt = conn.prepareStatement(updSlot);
                updateSlotStmt.setInt(1, slotId);
                updateSlotStmt.executeUpdate();

                String updBook = "UPDATE dbo.Booking SET status = 'canceled' WHERE booking_id = ?";
                updateBookingStmt = conn.prepareStatement(updBook);
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
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
        return success;
    }

    // Permanently removes a booking from the history.
    public boolean removeBooking(int bookingId) throws Exception {
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = DbUtils.getConnection();
            String sql = "DELETE FROM dbo.Booking WHERE booking_id = ? AND status != 'active'";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, bookingId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } finally {
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }

    // Updates an existing booking's details.
    public boolean editBooking(Booking booking) throws Exception {
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = DbUtils.getConnection();
            String sql = "UPDATE dbo.Booking SET license_plate = ?, vehicle_type_id = ?, slot_id = ?, target_time = ? WHERE booking_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, booking.getLicensePlate());
            stmt.setInt(2, booking.getVehicleTypeId());
            stmt.setInt(3, booking.getSlotId());
            
            String formattedTime = booking.getTargetTime();
            if (formattedTime.contains("T")) {
                formattedTime = formattedTime.replace("T", " ");
                if (formattedTime.length() == 16) {
                    formattedTime += ":00";
                }
            }
            stmt.setString(4, formattedTime);
            stmt.setInt(5, booking.getBookingId());
            
            int rows = stmt.executeUpdate();
            return rows > 0;
        } finally {
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }
}
