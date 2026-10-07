package com.payflow.infrastructure.gateway.razorpay;

import com.payflow.common.exception.BusinessException;
import com.payflow.infrastructure.gateway.GatewayRefundRequest;
import com.payflow.infrastructure.gateway.GatewayRefundResponse;
import com.payflow.infrastructure.gateway.RefundGateway;
import com.razorpay.Payment;
import com.razorpay.Refund;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Razorpay Refund Gateway Implementation
 * 
 * Integrates with Razorpay Refund API.
 * Translates between PayFlow domain models and Razorpay API models.
 * 
 * Amount Conversion:
 * - PayFlow uses BigDecimal (e.g., ₹100.00)
 * - Razorpay requires smallest currency unit (e.g., 10000 paise)
 */
@Component("razorpayRefundGateway")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "razorpay.key-id")
@Primary
public class RazorpayRefundGateway implements RefundGateway {

    private final RazorpayProperties razorpayProperties;
    private RazorpayClient razorpayClient;

    @PostConstruct
    public void init() {
        try {
            if (razorpayProperties.getKeyId() == null || razorpayProperties.getKeyId().trim().isEmpty()) {
                log.warn("Razorpay Key ID not configured. RazorpayRefundGateway will not be available.");
                return;
            }
            
            if (razorpayProperties.getKeySecret() == null || razorpayProperties.getKeySecret().trim().isEmpty()) {
                log.warn("Razorpay Key Secret not configured. RazorpayRefundGateway will not be available.");
                return;
            }

            this.razorpayClient = new RazorpayClient(
                razorpayProperties.getKeyId(), 
                razorpayProperties.getKeySecret()
            );
            
            log.info("RazorpayRefundGateway initialized in {} mode", 
                razorpayProperties.isTestMode() ? "TEST" : "LIVE");
        } catch (RazorpayException e) {
            log.error("Failed to initialize Razorpay refund client", e);
            throw new BusinessException("GATEWAY_INIT_FAILED", "Failed to initialize Razorpay refund gateway");
        }
    }

    @Override
    public GatewayRefundResponse createRefund(GatewayRefundRequest request) {
        log.info("Razorpay Gateway: Creating refund {} for payment {}, amount: {} {}", 
            request.getInternalRefundId(), 
            request.getGatewayPaymentId(),
            request.getAmount(),
            request.getCurrency());
        
        if (razorpayClient == null) {
            return GatewayRefundResponse.builder()
                    .success(false)
                    .errorCode("GATEWAY_NOT_CONFIGURED")
                    .message("Razorpay refund gateway is not properly configured")
                    .build();
        }

        try {
            // Convert amount to smallest currency unit (paise for INR)
            int amountInSmallestUnit = convertToSmallestUnit(request.getAmount(), request.getCurrency());
            
            // Build refund request
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", amountInSmallestUnit);
            
            if (request.getReason() != null && !request.getReason().isEmpty()) {
                JSONObject notes = new JSONObject();
                notes.put("reason", request.getReason());
                notes.put("internal_refund_id", request.getInternalRefundId());
                refundRequest.put("notes", notes);
            }

            // Create refund via Razorpay SDK
            Refund razorpayRefund = razorpayClient.payments.refund(
                request.getGatewayPaymentId(), 
                refundRequest
            );
            
            log.info("Razorpay refund created: refund_id={}, payment_id={}, status={}", 
                razorpayRefund.get("id"),
                request.getGatewayPaymentId(),
                razorpayRefund.get("status"));

            // Map response
            return GatewayRefundResponse.builder()
                    .success(true)
                    .gatewayRefundId(razorpayRefund.get("id"))
                    .gatewayStatus(razorpayRefund.get("status"))
                    .amount(convertFromSmallestUnit(razorpayRefund.get("amount"), request.getCurrency()))
                    .currency(razorpayRefund.get("currency"))
                    .message("Razorpay refund created successfully")
                    .build();

        } catch (RazorpayException e) {
            log.error("Razorpay refund creation failed: refund={}, payment={}", 
                request.getInternalRefundId(), request.getGatewayPaymentId(), e);
            return mapRazorpayException(e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid refund amount: {}", request.getAmount(), e);
            return GatewayRefundResponse.builder()
                    .success(false)
                    .errorCode("INVALID_AMOUNT")
                    .message("Invalid refund amount: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error during Razorpay refund creation: {}", request.getInternalRefundId(), e);
            return GatewayRefundResponse.builder()
                    .success(false)
                    .errorCode("GATEWAY_INTERNAL_ERROR")
                    .message("Internal gateway error occurred")
                    .build();
        }
    }

    @Override
    public String getGatewayName() {
        return "RAZORPAY";
    }

    /**
     * Converts BigDecimal amount to smallest currency unit (paise for INR).
     * 
     * Examples:
     * - ₹100.00 → 10000 paise
     * - ₹0.01 → 1 paise
     * 
     * @throws IllegalArgumentException if amount has more than 2 decimal places
     */
    int convertToSmallestUnit(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        // Validate scale (max 2 decimal places for INR)
        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                String.format("Amount %s has more than 2 decimal places", amount));
        }

        // Convert to smallest unit (multiply by 100 for INR/paise)
        BigDecimal amountInSmallestUnit = amount.multiply(new BigDecimal("100"));
        
        // Ensure no fractional paise
        if (amountInSmallestUnit.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                String.format("Amount %s cannot be represented in smallest currency unit", amount));
        }

        // Convert to int
        try {
            return amountInSmallestUnit.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                String.format("Amount %s is too large for gateway", amount));
        }
    }

    /**
     * Converts smallest currency unit back to BigDecimal.
     * 
     * Examples:
     * - 10000 paise → ₹100.00
     * - 1 paise → ₹0.01
     */
    BigDecimal convertFromSmallestUnit(Object amountObj, String currency) {
        if (amountObj == null) {
            return BigDecimal.ZERO;
        }

        int amountInSmallestUnit;
        if (amountObj instanceof Integer) {
            amountInSmallestUnit = (Integer) amountObj;
        } else if (amountObj instanceof String) {
            amountInSmallestUnit = Integer.parseInt((String) amountObj);
        } else {
            amountInSmallestUnit = ((Number) amountObj).intValue();
        }

        return new BigDecimal(amountInSmallestUnit)
            .divide(new BigDecimal("100"), 2, RoundingMode.UNNECESSARY);
    }

    /**
     * Maps Razorpay exceptions to gateway responses
     */
    private GatewayRefundResponse mapRazorpayException(RazorpayException e) {
        String errorCode = "GATEWAY_ERROR";
        String message = "Gateway refund operation failed";
        
        String exceptionMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        
        if (exceptionMessage.contains("bad request") || exceptionMessage.contains("invalid")) {
            errorCode = "GATEWAY_INVALID_REQUEST";
            message = "Invalid refund request parameters";
        } else if (exceptionMessage.contains("unauthorized") || exceptionMessage.contains("authentication")) {
            errorCode = "GATEWAY_UNAUTHORIZED";
            message = "Gateway authentication failed";
        } else if (exceptionMessage.contains("payment") && exceptionMessage.contains("not found")) {
            errorCode = "PAYMENT_NOT_FOUND";
            message = "Payment not found in gateway";
        } else if (exceptionMessage.contains("already") && exceptionMessage.contains("refund")) {
            errorCode = "ALREADY_REFUNDED";
            message = "Payment already fully refunded";
        } else if (exceptionMessage.contains("insufficient")) {
            errorCode = "INSUFFICIENT_AMOUNT";
            message = "Insufficient amount available for refund";
        } else if (exceptionMessage.contains("timeout") || exceptionMessage.contains("connection")) {
            errorCode = "GATEWAY_TIMEOUT";
            message = "Gateway request timed out";
        } else if (exceptionMessage.contains("rate limit")) {
            errorCode = "RATE_LIMIT_EXCEEDED";
            message = "Gateway rate limit exceeded";
        } else if (exceptionMessage.contains("server error")) {
            errorCode = "GATEWAY_SERVER_ERROR";
            message = "Gateway server error";
        } else {
            message = e.getMessage() != null ? e.getMessage() : "Unknown gateway error";
        }
        
        return GatewayRefundResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}
