package utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/*
 * VNPayService — builds the payment URL that sends customers to the VNPay sandbox.
 *
 * The flow works like this:
 *   1. We gather the booking details and create a sorted map of request parameters.
 *   2. We sign that map with our HMAC-SHA512 secret key to create the `vnp_SecureHash`.
 *   3. We build the full URL and redirect the customer's browser to VNPay.
 *   4. After the customer pays, VNPay sends them back to our return URL,
 *      where we verify the hash to confirm VNPay actually sent the response.
 *
 * Credentials are from the VNPay Sandbox issued to this project.
 * Do not use these in production — request production keys from VNPay directly.
 */
public class VNPayService {

    private static final String VNP_TMN_CODE   = "L0DAW9MF";
    private static final String VNP_HASH_SECRET = "J8QYH7P5CJ37NR5E9WFMH30AGZ0AFEZO";
    private static final String VNP_URL         = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String VNP_VERSION     = "2.1.0";
    private static final String VNP_COMMAND     = "pay";
    private static final String VNP_CURR_CODE   = "VND";
    private static final String VNP_LOCALE      = "vn";
    private static final String VNP_ORDER_TYPE  = "other";

    /*
     * Generates a unique order reference to use as vnp_TxnRef.
     * This ties the VNPay callback back to a specific booking row in our DB.
     * Format: booking ID + timestamp, e.g. "42-20260815100512"
     */
    public static String generateTxnRef(int bookingId) {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return bookingId + "-" + timestamp;
    }

    /*
     * Builds the full VNPay payment URL for a given booking.
     *
     * amount       — booking amount in VND (e.g. 50000 for 50,000 VND)
     * txnRef       — the unique reference linking this payment to our booking record
     * orderInfo    — a short description shown to the customer on the payment page
     * returnUrl    — where VNPay sends the customer after they finish paying
     * clientIp     — the customer's IP address (required by VNPay for fraud detection)
     */
    public static String buildPaymentUrl(long amount, String txnRef, String orderInfo, String returnUrl, String clientIp) throws Exception {
        Map<String, String> params = new TreeMap<>(); // TreeMap sorts keys alphabetically for signing
        params.put("vnp_Version",    VNP_VERSION);
        params.put("vnp_Command",    VNP_COMMAND);
        params.put("vnp_TmnCode",    VNP_TMN_CODE);
        params.put("vnp_Amount",     String.valueOf(amount * 100)); // VNPay expects amount * 100
        params.put("vnp_CurrCode",   VNP_CURR_CODE);
        params.put("vnp_TxnRef",     txnRef);
        params.put("vnp_OrderInfo",  orderInfo);
        params.put("vnp_OrderType",  VNP_ORDER_TYPE);
        params.put("vnp_Locale",     VNP_LOCALE);
        params.put("vnp_ReturnUrl",  returnUrl);
        params.put("vnp_IpAddr",     clientIp);
        params.put("vnp_CreateDate", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));

        // Build the raw data string by joining all params as "key=value" with "&"
        StringBuilder rawData = new StringBuilder();
        StringBuilder query   = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            rawData.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII.toString()));
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII.toString()))
                 .append("=")
                 .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII.toString()));
            rawData.append("&");
            query.append("&");
        }
        // Remove trailing "&"
        String rawDataStr = rawData.toString();
        if (rawDataStr.endsWith("&")) rawDataStr = rawDataStr.substring(0, rawDataStr.length() - 1);
        String queryStr = query.toString();
        if (queryStr.endsWith("&")) queryStr = queryStr.substring(0, queryStr.length() - 1);

        String secureHash = hmacSHA512(VNP_HASH_SECRET, rawDataStr);
        return VNP_URL + "?" + queryStr + "&vnp_SecureHash=" + secureHash;
    }

    /*
     * Verifies that a VNPay callback is genuine by recomputing the HMAC and comparing it
     * against the hash VNPay included in the redirect URL.
     * Returns true only if the hash matches, meaning the data wasn't tampered with.
     */
    public static boolean verifyReturnHash(Map<String, String> params) throws Exception {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null) return false;

        // Rebuild the signed params WITHOUT the hash fields themselves
        Map<String, String> signParams = new TreeMap<>(params);
        signParams.remove("vnp_SecureHash");
        signParams.remove("vnp_SecureHashType");

        StringBuilder rawData = new StringBuilder();
        for (Map.Entry<String, String> entry : signParams.entrySet()) {
            rawData.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII.toString())).append("&");
        }
        String rawDataStr = rawData.toString();
        if (rawDataStr.endsWith("&")) rawDataStr = rawDataStr.substring(0, rawDataStr.length() - 1);

        String expectedHash = hmacSHA512(VNP_HASH_SECRET, rawDataStr);
        return expectedHash.equalsIgnoreCase(receivedHash);
    }

    // Standard HMAC-SHA512 signing used by VNPay to secure all communications
    private static String hmacSHA512(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
