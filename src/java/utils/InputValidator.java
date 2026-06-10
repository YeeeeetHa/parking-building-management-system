/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utils;

/*
 * InputValidator — thin utility for basic input sanitation
 *
 * Used by LoginApiController before touching the DB.
 * Strategy here is a blacklist (block known-bad chars) rather than a whitelist —
 * simpler to implement but less strict. The frontend (login.html) mirrors these
 * same checks in JavaScript so users get instant feedback, but the server-side
 * check here is the authoritative gate.
 */
public class InputValidator {

    // Returns true if the input is safe to use, false if it looks sketchy.
    // maxLength lets callers enforce field-specific size limits (username=100, password=30, etc.).
    public static boolean isValidLoginInput(String input, int maxLength) {
        if (input == null) {
            return false;
        }
        input = input.trim();
        if (input.isEmpty() || input.length() > maxLength) {
            return false;
        }
        // Blacklist block: Blocks dangerous database manipulation symbols 
        // but perfectly allows spaces for "Nguyen Admin" and numbers like "123"
        if (input.contains("'") || input.contains("\"") || input.contains(";") || input.contains("--")) {
            return false;
        }
        return true;
    }

    public static boolean isValidLicensePlate(String plate) {
        if (plate == null || plate.trim().isEmpty()) {
            return false;
        }
        return plate.trim().toUpperCase().matches("^[A-Z0-9\\.\\-\\ ]+$");
    }
}
