/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utils;

public class InputValidator {

    /**
     * Sanitizes and validates login input strings.
     * @param input The raw input from the request
     * @param maxLength The maximum allowed characters
     * @return true if valid, false if empty, too long, or contains illegal characters
     */
    public static boolean isValidLoginInput(String input, int maxLength) {
        if (input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.length() > maxLength) {
            return false;
        }
        if (trimmed.contains("'") || trimmed.contains("\"") || trimmed.contains(";") || 
            trimmed.contains("--") || trimmed.contains("<") || trimmed.contains(">")) {
            return false;
        }
        return true;
    }
}