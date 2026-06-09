package dto;

/*
 * Booking — carries advance reservation data
 * from BookingAPIController down to BookingDAO.
 *
 * Each field maps to a column in the database to maintain clean data transfer.
 */
public class Booking {
    private String licensePlate;
    private int vehicleTypeId;
    private int slotId;
    private String targetTime;

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    
    public int getVehicleTypeId() { return vehicleTypeId; }
    public void setVehicleTypeId(int vehicleTypeId) { this.vehicleTypeId = vehicleTypeId; }
    
    public int getSlotId() { return slotId; }
    public void setSlotId(int slotId) { this.slotId = slotId; }
    
    public String getTargetTime() { return targetTime; }
    public void setTargetTime(String targetTime) { this.targetTime = targetTime; }
}
