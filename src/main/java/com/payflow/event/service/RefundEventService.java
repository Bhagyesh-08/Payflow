package com.payflow.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.util.IdGenerator;
import com.payflow.event.domain.RefundEvent;
import com.payflow.event.domain.RefundEventType;
import com.payflow.event.outbox.OutboxEvent;
import com.payflow.event.outbox.OutboxEventRepository;
import com.payflow.event.outbox.OutboxStatus;
import com.payflow.refund.entity.Refund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Refund Event Service.
 * 
 * Creates domain events and stores them in the transactional outbox.
 * Events are persisted in the same transaction as the refund state change,
 * preventing the dual-write problem (DB success + Kafka failure).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates a domain event for a refund state change.
     * MUST be called within an existing transaction.
     * 
     * @param refund the refund entity
     * @param eventType the type of event
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createRefundEvent(Refund refund, RefundEventType eventType) {
        try {
            // Generate unique event ID (separate from refund ID)
            String eventId = IdGenerator.generateEventId();

            // Build domain event
            RefundEvent event = RefundEvent.builder()
                .eventId(eventId)
                .eventType(eventType.name())
                .occurredAt(LocalDateTime.now())
                .aggregateType("REFUND")
                .aggregateId(refund.getRefundId())
                .merchantId(refund.getMerchant().getId())
                .version(1)
                .data(RefundEvent.RefundEventData.builder()
                    .refundId(refund.getRefundId())
                    .paymentId(refund.getPayment().getPaymentId())
                    .merchantId(refund.getMerchant().getId())
                    .amount(refund.getAmount())
                    .currency(refund.getCurrency())
                    .status(refund.getStatus().name())
                    .gateway(refund.getGateway())
                    .gatewayRefundId(refund.getGatewayRefundId())
                    .reason(refund.getReason())
                    .failureReason(refund.getFailureReason())
                    .build())
                .build();

            // Serialize to JSON
            String payload = objectMapper.writeValueAsString(event);

            // Store in outbox (same transaction as refund update)
            OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("REFUND")
                .aggregateId(refund.getRefundId())
                .eventType(eventType.name())
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .build();

            outboxEventRepository.save(outboxEvent);

            log.info("Refund event created: event_id={}, event_type={}, refund_id={}", 
                eventId, eventType, refund.getRefundId());

        } catch (Exception e) {
            log.error("Failed to create refund event: refund_id={}, event_type={}", 
                refund.getRefundId(), eventType, e);
            // Throwing exception will rollback the entire transaction (refund + event)
            throw new RuntimeException("Failed to create refund event", e);
        }
    }
}
