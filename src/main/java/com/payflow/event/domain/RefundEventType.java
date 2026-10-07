package com.payflow.event.domain;

/**
 * Refund Event Types.
 * 
 * Each represents a significant state transition in the refund lifecycle.
 */
public enum RefundEventType {
    REFUND_CREATED,
    REFUND_SUCCEEDED,
    REFUND_FAILED
}
