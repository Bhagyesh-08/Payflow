package com.payflow.infrastructure.gateway;

import java.math.BigDecimal;

/**
 * Payment Gateway Interface
 * 
 * Abstracts payment gateway operations to allow multiple implementations
 * (Mock, Razorpay, Stripe, etc.) without coupling domain logic to specific gateway.
 */
public interface PaymentGateway {

    /**
     * Initiates a payment with the gateway.
     *
     * @param request the payment initiation request
     * @return the gateway response
     */
    GatewayResponse initiatePayment(GatewayPaymentRequest request);

    /**
     * Authorizes a payment (reserves funds).
     *
     * @param gatewayPaymentId the gateway's payment identifier
     * @return the authorization response
     */
    GatewayResponse authorizePayment(String gatewayPaymentId);

    /**
     * Captures an authorized payment (transfers funds).
     *
     * @param gatewayPaymentId the gateway's payment identifier
     * @param amount the amount to capture
     * @return the capture response
     */
    GatewayResponse capturePayment(String gatewayPaymentId, BigDecimal amount);

    /**
     * Gets the gateway name/type.
     *
     * @return gateway identifier
     */
    String getGatewayName();
}
