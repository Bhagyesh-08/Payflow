package com.payflow.infrastructure.gateway;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Gateway Refund Request DTO
 * 
 * Internal representation of a refund request to be sent to a gateway.
 * Gateway-agnostic model.
 */
@Data
@Builder
public class GatewayRefundRequest {

    /**
     * Internal PayFlow refund ID
     */
    private String internalRefundId;

    /**
     * Gateway payment ID to be refunded
     */
    private String gatewayPaymentId;

    /**
     * Refund amount
     */
    private BigDecimal amount;

    /**
     * Currency code (e.g., "INR", "USD")
     */
    private String currency;

    /**
     * Refund reason/notes
     */
    private String reason;
}
