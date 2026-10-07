package com.payflow.infrastructure.gateway.razorpay;

import com.payflow.common.exception.BusinessException;
import com.payflow.infrastructure.gateway.GatewayPaymentRequest;
import com.payflow.infrastructure.gateway.GatewayResponse;
import com.payflow.infrastructure.gateway.PaymentGateway;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;

/**
 * Razorpay Payment Gateway Implementation
 * 
 * Integrates with Razorpay Test API for payment processing.
 * Translates between PayFlow domain models and Razorpay API models.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "razorpay.key-id")
@Primary
public class RazorpayGateway implements PaymentGateway {

    private final RazorpayProperties razorpayProperties;
    private RazorpayClient razorpayClient;

    @PostConstruct
    public void init() {
        try {
            if (razorpayProperties.getKeyId() == null || razorpayProperties.getKeyId().trim().isEmpty()) {
                log.warn("Razorpay Key ID not configured. RazorpayGateway will not be available.");
                return;
            }
            
            if (razorpayProperties.getKeySecret() == null || razorpayProperties.getKeySecret().trim().isEmpty()) {
                log.warn("Razorpay Key Secret not configured. RazorpayGateway will not be available.");
                return;
            }

            this.razorpayClient = new RazorpayClient(
                razorpayProperties.getKeyId(), 
                razorpayProperties.getKeySecret()
            );
            
            log.info("RazorpayGateway initialized in {} mode", 
                razorpayProperties.isTestMode() ? "TEST" : "LIVE");
        } catch (RazorpayException e) {
            log.error("Failed to initialize Razorpay client", e);
            throw new BusinessException("GATEWAY_INIT_FAILED", "Failed to initialize Razorpay gateway");
        }
    }

    @Override
    public GatewayResponse initiatePayment(GatewayPaymentRequest request) {
        log.info("Razorpay Gateway: Initiating payment for internal ID: {}", request.getInternalPaymentId());
        
        if (razorpayClient == null) {
            return GatewayResponse.builder()
                    .success(false)
                    .errorCode("GATEWAY_NOT_CONFIGURED")
                    .message("Razorpay gateway is not properly configured")
                    .build();
        }

        try {
            // Convert amount to paise (smallest currency unit)
            int amountInPaise = request.getAmount().multiply(new BigDecimal("100")).intValue();
            
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", request.getCurrency());
            orderRequest.put("receipt", request.getInternalPaymentId());
            
            // Add customer details if available
            if (request.getCustomerEmail() != null) {
                JSONObject notes = new JSONObject();
                notes.put("customer_email", request.getCustomerEmail());
                notes.put("payment_method", request.getPaymentMethod());
                orderRequest.put("notes", notes);
            }

            Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            
            log.info("Razorpay order created: {} for internal payment: {}", 
                razorpayOrder.get("id"), request.getInternalPaymentId());

            return GatewayResponse.builder()
                    .success(true)
                    .gatewayPaymentId(razorpayOrder.get("id"))
                    .gatewayStatus(razorpayOrder.get("status"))
                    .message("Razorpay order created successfully")
                    .build();

        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed for payment: {}", request.getInternalPaymentId(), e);
            return mapRazorpayException(e);
        } catch (Exception e) {
            log.error("Unexpected error during Razorpay order creation: {}", request.getInternalPaymentId(), e);
            return GatewayResponse.builder()
                    .success(false)
                    .errorCode("GATEWAY_INTERNAL_ERROR")
                    .message("Internal gateway error occurred")
                    .build();
        }
    }

    @Override
    public GatewayResponse authorizePayment(String gatewayPaymentId) {
        log.info("Razorpay Gateway: Authorizing payment {}", gatewayPaymentId);
        
        if (razorpayClient == null) {
            return GatewayResponse.builder()
                    .success(false)
                    .errorCode("GATEWAY_NOT_CONFIGURED")
                    .message("Razorpay gateway is not properly configured")
                    .build();
        }

        try {
            // In Razorpay, authorization happens when payment is captured
            // For now, we'll fetch the order status to simulate authorization
            Order order = razorpayClient.orders.fetch(gatewayPaymentId);
            String status = order.get("status");
            
            boolean isAuthorized = "created".equals(status) || "attempted".equals(status) || "paid".equals(status);
            
            return GatewayResponse.builder()
                    .success(isAuthorized)
                    .gatewayPaymentId(gatewayPaymentId)
                    .gatewayStatus(status)
                    .message(isAuthorized ? "Payment authorized" : "Payment not ready for authorization")
                    .errorCode(isAuthorized ? null : "PAYMENT_NOT_AUTHORIZED")
                    .build();

        } catch (RazorpayException e) {
            log.error("Razorpay authorization check failed for payment: {}", gatewayPaymentId, e);
            return mapRazorpayException(e);
        } catch (Exception e) {
            log.error("Unexpected error during Razorpay authorization: {}", gatewayPaymentId, e);
            return GatewayResponse.builder()
                    .success(false)
                    .errorCode("GATEWAY_INTERNAL_ERROR")
                    .message("Internal gateway error occurred")
                    .build();
        }
    }

    @Override
    public GatewayResponse capturePayment(String gatewayPaymentId, BigDecimal amount) {
        log.info("Razorpay Gateway: Capturing payment {} for amount {}", gatewayPaymentId, amount);
        
        if (razorpayClient == null) {
            return GatewayResponse.builder()
                    .success(false)
                    .errorCode("GATEWAY_NOT_CONFIGURED")
                    .message("Razorpay gateway is not properly configured")
                    .build();
        }

        try {
            // Convert amount to paise
            int amountInPaise = amount.multiply(new BigDecimal("100")).intValue();
            
            JSONObject captureRequest = new JSONObject();
            captureRequest.put("amount", amountInPaise);
            captureRequest.put("currency", "INR");
            
            // In real implementation, this would capture a specific payment
            // For now, we'll simulate successful capture for test mode
            if (razorpayProperties.isTestMode()) {
                log.info("Test mode: Simulating successful capture for {}", gatewayPaymentId);
                return GatewayResponse.builder()
                        .success(true)
                        .gatewayPaymentId(gatewayPaymentId)
                        .gatewayStatus("CAPTURED")
                        .message("Payment captured successfully (test mode)")
                        .build();
            }

            // In live mode, would use actual capture API
            return GatewayResponse.builder()
                    .success(false)
                    .errorCode("CAPTURE_NOT_IMPLEMENTED")
                    .message("Live capture not implemented in this phase")
                    .build();

        } catch (Exception e) {
            log.error("Unexpected error during Razorpay capture: {}", gatewayPaymentId, e);
            return GatewayResponse.builder()
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
     * Maps Razorpay exceptions to gateway responses
     */
    private GatewayResponse mapRazorpayException(RazorpayException e) {
        String errorCode = "GATEWAY_ERROR";
        String message = "Gateway operation failed";
        
        // Map based on exception message since SDK doesn't expose error codes consistently
        String exceptionMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        
        if (exceptionMessage.contains("bad request") || exceptionMessage.contains("invalid")) {
            errorCode = "GATEWAY_INVALID_REQUEST";
            message = "Invalid request parameters";
        } else if (exceptionMessage.contains("unauthorized") || exceptionMessage.contains("authentication")) {
            errorCode = "GATEWAY_UNAUTHORIZED";
            message = "Gateway authentication failed";
        } else if (exceptionMessage.contains("timeout") || exceptionMessage.contains("connection")) {
            errorCode = "GATEWAY_TIMEOUT";
            message = "Gateway request timed out";
        } else if (exceptionMessage.contains("server error")) {
            errorCode = "GATEWAY_SERVER_ERROR";
            message = "Gateway server error";
        } else {
            message = e.getMessage() != null ? e.getMessage() : "Unknown gateway error";
        }
        
        return GatewayResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}