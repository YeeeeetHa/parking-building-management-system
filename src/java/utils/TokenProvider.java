package utils;


import java.security.SecureRandom;
import java.util.Base64;

public class TokenProvider {
    // SecureRandom (not Math.random) — cryptographically strong, essential for auth tokens
    private static final SecureRandom secureRandom = new SecureRandom();
    // URL-safe encoder so the token can be safely included in HTTP headers / URLs
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();

    // Generates a fresh random token each time — 32 bytes = 256 bits of entropy,
    // which makes brute-forcing effectively impossible
    public static String generateNewToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }
}