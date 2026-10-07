package com.payflow.infrastructure.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private MockPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MockPaymentGateway();
    }

    @Test
    void initiatePayment_ReturnsSuccessResponse() {
        GatewayPaymentRequest request = GatewayPaymentRequest.builder()
                .internalPaymentId("PAY_TEST")
                .amount(new BigDecimal("1000"))
                .currency("INR")
                .paymentMethod("UPI")
                .build();

        GatewayResponse response = gateway.initiatePayment(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getGatewayPaymentId()).startsWith("MOCK_");
        assertThat(response.getGatewayStatus()).isEqualTo("INITIATED");
    }

    @Test
    void authorizePayment_ReturnsSuccessResponse() {
        GatewayResponse response = gateway.authorizePayment("MOCK_123");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getGatewayPaymentId()).isEqualTo("MOCK_123");
        assertThat(response.getGatewayStatus()).isEqualTo("AUTHORIZED");
    }

    @Test
    void capturePayment_ReturnsSuccessResponse() {
        GatewayResponse response = gateway.capturePayment("MOCK_123", new BigDecimal("1000"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getGatewayPaymentId()).isEqualTo("MOCK_123");
        assertThat(response.getGatewayStatus()).isEqualTo("CAPTURED");
    }
}