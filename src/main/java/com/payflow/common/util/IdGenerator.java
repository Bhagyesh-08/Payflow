package com.payflow.common.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * ID Generator Utility.
 * 
 * Generates unique IDs for domain entities.
 */
public class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public static String generatePaymentId() {
        return "PAY_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    public static String generateOrderId() {
        return "ORD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    public static String generateRefundId() {
        return "RFD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    public static String generateDisputeId() {
        return "DSP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    public static String generateEventId() {
        return "EVT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    public static String generateIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    public static String generateOrderNumber() {
        return "ORD_" + System.currentTimeMillis() + "_" + generateRandomString(6);
    }

    public static String generateMerchantCode() {
        return "MRCH_" + generateRandomString(12);
    }

    public static String generateApiKey() {
        return "sk_" + generateRandomString(32);
    }

    private static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
