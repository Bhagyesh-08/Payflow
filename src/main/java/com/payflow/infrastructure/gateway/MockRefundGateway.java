package com.payflow.infrastructure.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mock Refund Gateway Implementation
 * 
 * Deterministic test implementation for unit testing.
 * Activated when no real gateway is configured.
 */
@Component
@Slf4j
@ConditionalOnMissingBean(name = "razorpayRefundGateway")
public class MockRefundGateway implements RefundGateway {

    @Override
    public GatewayRefundResponse createRefund(GatewayRefundRequest request) {
        log.info("Mock Gateway: Creating refund {} for payment {}", 
            request.getInternalRefundId(), request.getGatewayPaymentId());

        // Simulate success by default
        return GatewayRefundResponse.builder()
            .success(true)
            .gatewayRefundId("MOCK_RFN_" + request.getInternalRefundId())
            .gatewayStatus("created")
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .message("Mock refund created successfully")
            .build();
    }

    @Override
    public String getGatewayName() {
        return "MOCK";
    }
}
