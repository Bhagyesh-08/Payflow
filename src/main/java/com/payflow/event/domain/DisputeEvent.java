package com.payflow.event.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dispute Domain Event.
 * 
 * Envelope pattern for dispute lifecycle events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private String aggregateType;
    private String aggregateId;
    private Long merchantId;
    private Integer version;
    private DisputeEventData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisputeEventData {
        private String disputeId;
        private String paymentId;
        private Long merchantId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String gateway;
        private String gatewayDisputeId;
        private String reason;
        private String resolutionReason;
    }
}
