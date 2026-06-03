/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dto;

import utils.StaffRole;
import java.time.LocalDateTime;

/**
 *
 * @author ASUS
 */
public class Staff {
    private String staff_id;
    private String name;
    private int phone;
    private StaffRole role;
    private LocalDateTime createAt = LocalDateTime.now();
    private String status = "active";
    public boolean isValidAttendant() {
        return "active".equalsIgnoreCase(this.status);
    }

    public Staff() {
    }

    public Staff(String staff_id, String name, int phone, StaffRole role) {
        this.staff_id = staff_id;
        this.name = name;
        this.phone = phone;
        this.role = role;
    }

    public String getStaff_id() {
        return staff_id;
    }

    public void setStaff_id(String staff_id) {
        this.staff_id = staff_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPhone() {
        return phone;
    }

    public void setPhone(int phone) {
        this.phone = phone;
    }

    public StaffRole getRole() {
        return role;
    }

    public void setRole(StaffRole role) {
        this.role = role;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Staff{" + "staff_id=" + staff_id + ", name=" + name + ", phone=" + phone + ", role=" + role + ", createAt=" + createAt + ", status=" + status + '}';
    }
    
}
