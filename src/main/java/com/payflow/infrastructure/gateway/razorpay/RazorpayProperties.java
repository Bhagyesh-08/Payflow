package com.payflow.infrastructure.gateway.razorpay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Razorpay configuration properties.
 * Credentials are sourced from environment variables for security.
 */
@Data
@Component
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayProperties {

    /**
     * Razorpay Key ID (public key)
     * Environment variable: RAZORPAY_KEY_ID
     */
    private String keyId;

    /**
     * Razorpay Key Secret (private key)
     * Environment variable: RAZORPAY_KEY_SECRET
     */
    private String keySecret;

    /**
     * Razorpay API timeout in seconds
     * Default: 30 seconds
     */
    private int timeoutSeconds = 30;

    /**
     * Whether to use test mode
     * Default: true for safety
     */
    private boolean testMode = true;

    /**
     * Razorpay Webhook Secret
     * Environment variable: RAZORPAY_WEBHOOK_SECRET
     */
    private String webhookSecret;
}