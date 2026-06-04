/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utils;

public class InputValidator {

    // Returns true if the input is safe to use, false if it looks sketchy.
    // maxLength lets callers enforce field-specific size limits (username=100, password=30, etc.).
    public static boolean isValidLoginInput(String input, int maxLength) {
        if (input == null) return false;
        input = input.trim();
        if (input.isEmpty() || input.length() > maxLength) return false;
        // Blacklist block: Blocks dangerous database manipulation symbols 
        // but perfectly allows spaces for "Nguyen Admin" and numbers like "123"
        if (input.contains("'") || input.contains("\"") || input.contains(";") || input.contains("--")) return false;
        return true;
    }

    public static boolean isValidLicensePlate(String licensePlate) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}