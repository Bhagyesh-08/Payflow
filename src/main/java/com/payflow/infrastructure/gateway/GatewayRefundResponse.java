package com.payflow.infrastructure.gateway;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Gateway Refund Response DTO
 * 
 * Normalized internal representation of gateway refund response.
 * Isolates domain layer from specific gateway SDKs.
 */
@Data
@Builder
public class GatewayRefundResponse {

    /**
     * Whether the refund request was accepted by the gateway
     */
    private boolean success;

    /**
     * Gateway's refund identifier
     */
    private String gatewayRefundId;

    /**
     * Gateway's refund status (gateway-specific string)
     */
    private String gatewayStatus;

    /**
     * Refund amount acknowledged by gateway
     */
    private BigDecimal amount;

    /**
     * Currency code
     */
    private String currency;

    /**
     * Error code (for failed refunds)
     */
    private String errorCode;

    /**
     * Human-readable message
     */
    private String message;
}
