package com.payflow.event.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Refund Domain Event.
 * 
 * Represents a significant state change in the refund lifecycle.
 * Each event has a unique event ID separate from the refund ID.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundEvent {

    /**
     * Unique event identifier (NOT the refund ID).
     * A single refund can produce multiple events.
     */
    private String eventId;

    /**
     * Type of event (REFUND_CREATED, REFUND_SUCCEEDED, etc.)
     */
    private String eventType;

    /**
     * When the event occurred
     */
    private LocalDateTime occurredAt;

    /**
     * Type of aggregate (always "REFUND" for refund events)
     */
    private String aggregateType;

    /**
     * Refund ID (aggregate identifier)
     */
    private String aggregateId;

    /**
     * Merchant ID for partitioning and filtering
     */
    private Long merchantId;

    /**
     * Event schema version for future evolution
     */
    private Integer version;

    /**
     * Event-specific data
     */
    private RefundEventData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RefundEventData {
        private String refundId;
        private String paymentId;
        private Long merchantId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String gateway;
        private String gatewayRefundId;
        private String reason;
        private String failureReason;
    }
}
