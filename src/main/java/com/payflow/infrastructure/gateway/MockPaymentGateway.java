package com.payflow.infrastructure.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock Payment Gateway
 * 
 * Simulates a payment gateway for testing without external dependencies.
 * Provides deterministic behavior for testing payment flows.
 */
@Component
@Slf4j
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public GatewayResponse initiatePayment(GatewayPaymentRequest request) {
        log.info("Mock Gateway: Initiating payment for {}", request.getInternalPaymentId());
        
        // Simulate gateway generating an ID
        String gatewayPaymentId = "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        return GatewayResponse.builder()
                .success(true)
                .gatewayPaymentId(gatewayPaymentId)
                .gatewayStatus("INITIATED")
                .message("Payment initiated successfully")
                .build();
    }

    @Override
    public GatewayResponse authorizePayment(String gatewayPaymentId) {
        log.info("Mock Gateway: Authorizing payment {}", gatewayPaymentId);
        
        return GatewayResponse.builder()
                .success(true)
                .gatewayPaymentId(gatewayPaymentId)
                .gatewayStatus("AUTHORIZED")
                .message("Payment authorized successfully")
                .build();
    }

    @Override
    public GatewayResponse capturePayment(String gatewayPaymentId, BigDecimal amount) {
        log.info("Mock Gateway: Capturing payment {} for amount {}", gatewayPaymentId, amount);
        
        return GatewayResponse.builder()
                .success(true)
                .gatewayPaymentId(gatewayPaymentId)
                .gatewayStatus("CAPTURED")
                .message("Payment captured successfully")
                .build();
    }

    @Override
    public String getGatewayName() {
        return "MOCK";
    }
}
