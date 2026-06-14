package dto;

/*
 * Booking — carries advance reservation data between the data layer and the frontend.
 *
 * Now includes customer identity fields so staff can see who made the booking,
 * and payment fields to track whether someone paid via Cash or VNPay.
 */
public class Booking {

    private int bookingId;
    private String customerName;
    private String customerPhone;
    private String licensePlate;
    private int vehicleTypeId;
    private String vehicleTypeName;   // Joined from Vehicle_type
    private int slotId;
    private String slotCode;          // Joined from Parking_slot
    private String areaCode;          // Joined from Parking_area
    private String targetTime;
    private String createdAt;
    private String status;            // active, occupied, canceled
    private String paymentMethod;     // Cash, VNPay
    private String vnpayTxnRef;       // VNPay order reference, null for cash bookings
    private String cancellationReason;

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public int getVehicleTypeId() {
        return vehicleTypeId;
    }

    public void setVehicleTypeId(int vehicleTypeId) {
        this.vehicleTypeId = vehicleTypeId;
    }

    public String getVehicleTypeName() {
        return vehicleTypeName;
    }

    public void setVehicleTypeName(String vehicleTypeName) {
        this.vehicleTypeName = vehicleTypeName;
    }

    public int getSlotId() {
        return slotId;
    }

    public void setSlotId(int slotId) {
        this.slotId = slotId;
    }

    public String getSlotCode() {
        return slotCode;
    }

    public void setSlotCode(String slotCode) {
        this.slotCode = slotCode;
    }

    public String getAreaCode() {
        return areaCode;
    }

    public void setAreaCode(String areaCode) {
        this.areaCode = areaCode;
    }

    public String getTargetTime() {
        return targetTime;
    }

    public void setTargetTime(String targetTime) {
        this.targetTime = targetTime;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getVnpayTxnRef() {
        return vnpayTxnRef;
    }

    public void setVnpayTxnRef(String vnpayTxnRef) {
        this.vnpayTxnRef = vnpayTxnRef;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }
}
