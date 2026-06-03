/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utils;

/**
 *
 * @author ASUS
 */
/*
 * StaffRole — the three permission levels in the system
 *
 * Used when logging in: the role stored in the DB (as a plain VARCHAR)
 * gets parsed into this enum so the rest of the code can do type-safe comparisons.
 *
 * Hierarchy (highest to lowest access):
 *   admin     — full system access, user management, configuration
 *   manager   — reports, pricing, slot management
 *   attendant — check-in / check-out operations only
 *
 * login.html maps each role to a different dashboard URL via getDashUrl().
 */
public enum StaffRole {
    admin,
    manager,
    attendant
}