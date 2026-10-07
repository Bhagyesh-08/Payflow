package com.payflow.infrastructure.gateway.razorpay;

import com.payflow.infrastructure.gateway.GatewayRefundRequest;
import com.payflow.infrastructure.gateway.GatewayRefundResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class RazorpayRefundGatewayTest {

    @Test
    void convertToSmallestUnit_ValidAmount_ConvertsCorrectly() {
        // Given
        RazorpayRefundGateway gateway = createGatewayWithoutClient();
        
        // When & Then
        assertThat(gateway.convertToSmallestUnit(new BigDecimal("100.00"), "INR")).isEqualTo(10000);
        assertThat(gateway.convertToSmallestUnit(new BigDecimal("0.01"), "INR")).isEqualTo(1);
        assertThat(gateway.convertToSmallestUnit(new BigDecimal("1.50"), "INR")).isEqualTo(150);
        assertThat(gateway.convertToSmallestUnit(new BigDecimal("999.99"), "INR")).isEqualTo(99999);
    }

    @Test
    void convertToSmallestUnit_MoreThanTwoDecimals_ThrowsException() {
        // Given
        RazorpayRefundGateway gateway = createGatewayWithoutClient();
        
        // When & Then
        assertThatThrownBy(() -> gateway.convertToSmallestUnit(new BigDecimal("100.001"), "INR"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("more than 2 decimal places");
    }

    @Test
    void convertToSmallestUnit_FractionalSmallestUnit_ThrowsException() {
        // Given
        RazorpayRefundGateway gateway = createGatewayWithoutClient();
        
        // When & Then - 0.005 has 3 decimal places
        assertThatThrownBy(() -> gateway.convertToSmallestUnit(new BigDecimal("0.005"), "INR"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("more than 2 decimal places");
    }

    @Test
    void convertToSmallestUnit_NullAmount_ThrowsException() {
        // Given
        RazorpayRefundGateway gateway = createGatewayWithoutClient();
        
        // When & Then
        assertThatThrownBy(() -> gateway.convertToSmallestUnit(null, "INR"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    void convertFromSmallestUnit_ValidAmount_ConvertsCorrectly() {
        // Given
        RazorpayRefundGateway gateway = createGatewayWithoutClient();
        
        // When & Then
        assertThat(gateway.convertFromSmallestUnit(10000, "INR")).isEqualByComparingTo("100.00");
        assertThat(gateway.convertFromSmallestUnit(1, "INR")).isEqualByComparingTo("0.01");
        assertThat(gateway.convertFromSmallestUnit(150, "INR")).isEqualByComparingTo("1.50");
    }

    @Test
    void createRefund_UnconfiguredGateway_ReturnsError() {
        // Given
        RazorpayRefundGateway gateway = createGatewayWithoutClient();
        GatewayRefundRequest request = GatewayRefundRequest.builder()
            .internalRefundId("REF_123")
            .gatewayPaymentId("pay_123")
            .amount(new BigDecimal("100.00"))
            .currency("INR")
            .build();
        
        // When
        GatewayRefundResponse response = gateway.createRefund(request);
        
        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("GATEWAY_NOT_CONFIGURED");
    }

    @Test
    void getGatewayName_ReturnsRazorpay() {
        // Given
        RazorpayRefundGateway gateway = createGatewayWithoutClient();
        
        // When & Then
        assertThat(gateway.getGatewayName()).isEqualTo("RAZORPAY");
    }

    private RazorpayRefundGateway createGatewayWithoutClient() {
        RazorpayProperties properties = new RazorpayProperties();
        // Don't set credentials, so client won't initialize
        return new RazorpayRefundGateway(properties);
    }
}
