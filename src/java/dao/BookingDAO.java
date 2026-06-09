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
}
