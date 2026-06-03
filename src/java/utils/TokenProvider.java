package utils;


import java.security.SecureRandom;
import java.util.Base64;

/*
 * TokenProvider — generates a secure session token for authenticated staff
 *
 * Called by LoginApiController after a successful login to produce an access token.
 * That token is then stored in-memory by StaffService and returned to the frontend
 * for use in subsequent protected API calls.
 */
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