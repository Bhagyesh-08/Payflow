package com.payflow.event.domain;

/**
 * Payment Event Types.
 * 
 * Each represents a significant state transition in the payment lifecycle.
 */
public enum PaymentEventType {
    PAYMENT_CREATED,
    PAYMENT_INITIATED,
    PAYMENT_AUTHORIZED,
    PAYMENT_CAPTURED,
    PAYMENT_FAILED
}
