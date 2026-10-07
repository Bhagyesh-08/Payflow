package com.payflow.webhook.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.infrastructure.gateway.razorpay.RazorpayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Razorpay Webhook Signature Verifier
 * 
 * Verifies webhook signatures using HMAC-SHA256.
 * Must operate on the raw request body exactly as received.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookSignatureVerifier {

    private final RazorpayProperties razorpayProperties;

    /**
     * Verifies Razorpay webhook signature.
     * 
     * Razorpay uses HMAC SHA256 signature:
     * signature = HMAC_SHA256(webhook_secret, raw_body)
     * 
     * @param rawPayload the raw request body (must be exact bytes received)
     * @param receivedSignature the X-Razorpay-Signature header value
     * @throws BusinessException if signature is invalid
     */
    public void verifySignature(String rawPayload, String receivedSignature) {
        if (receivedSignature == null || receivedSignature.trim().isEmpty()) {
            log.error("Webhook signature missing");
            throw new BusinessException("WEBHOOK_SIGNATURE_MISSING", "Webhook signature is required");
        }

        String webhookSecret = razorpayProperties.getWebhookSecret();
        if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
            log.error("Webhook secret not configured");
            throw new BusinessException("WEBHOOK_SECRET_NOT_CONFIGURED", 
                "Webhook secret is not configured");
        }

        try {
            String computedSignature = generateHmacSHA256(rawPayload, webhookSecret);
            
            if (!MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8))) {
                log.error("Webhook signature verification failed");
                throw new BusinessException("WEBHOOK_SIGNATURE_INVALID", "Invalid webhook signature");
            }

            log.debug("Webhook signature verified successfully");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Webhook signature verification error", e);
            throw new BusinessException("WEBHOOK_SIGNATURE_ERROR", 
                "Failed to verify webhook signature");
        }
    }

    /**
     * Generates HMAC SHA256 signature.
     */
    private String generateHmacSHA256(String data, String key) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] signedBytes = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(signedBytes);
    }

    /**
     * Converts bytes to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
