package com.payflow.event.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Base Payment Domain Event.
 * 
 * Represents a significant state change in the payment lifecycle.
 * Each event has a unique event ID separate from the payment ID.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    /**
     * Unique event identifier (NOT the payment ID).
     * A single payment can produce multiple events.
     */
    private String eventId;

    /**
     * Type of event (PAYMENT_CREATED, PAYMENT_AUTHORIZED, etc.)
     */
    private String eventType;

    /**
     * When the event occurred
     */
    private LocalDateTime occurredAt;

    /**
     * Type of aggregate (always "PAYMENT" for payment events)
     */
    private String aggregateType;

    /**
     * Payment ID (aggregate identifier)
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
    private PaymentEventData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentEventData {
        private String paymentId;
        private Long orderId;
        private Long merchantId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String paymentMethod;
        private String gatewayPaymentId;
        private String failureReason;
    }
}
