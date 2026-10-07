package com.payflow.infrastructure.gateway;

import java.math.BigDecimal;

/**
 * Refund Gateway Interface
 * 
 * Abstracts refund gateway operations to allow multiple implementations
 * (Mock, Razorpay, Stripe, etc.) without coupling domain logic to specific gateway.
 */
public interface RefundGateway {

    /**
     * Creates a refund with the gateway.
     *
     * @param request the refund request
     * @return the gateway refund response
     */
    GatewayRefundResponse createRefund(GatewayRefundRequest request);

    /**
     * Gets the gateway name/type.
     *
     * @return gateway identifier
     */
    String getGatewayName();
}
