package com.payflow.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.util.IdGenerator;
import com.payflow.event.domain.PaymentEvent;
import com.payflow.event.domain.PaymentEventType;
import com.payflow.event.outbox.OutboxEvent;
import com.payflow.event.outbox.OutboxEventRepository;
import com.payflow.event.outbox.OutboxStatus;
import com.payflow.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Payment Event Service.
 * 
 * Creates domain events and stores them in the transactional outbox.
 * Events are persisted in the same transaction as the payment state change,
 * preventing the dual-write problem (DB success + Kafka failure).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates a domain event for a payment state change.
     * MUST be called within an existing transaction.
     * 
     * @param payment the payment entity
     * @param eventType the type of event
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createPaymentEvent(Payment payment, PaymentEventType eventType) {
        try {
            // Generate unique event ID (separate from payment ID)
            String eventId = IdGenerator.generateEventId();

            // Build domain event
            PaymentEvent event = PaymentEvent.builder()
                .eventId(eventId)
                .eventType(eventType.name())
                .occurredAt(LocalDateTime.now())
                .aggregateType("PAYMENT")
                .aggregateId(payment.getPaymentId())
                .merchantId(payment.getMerchant().getId())
                .version(1)
                .data(PaymentEvent.PaymentEventData.builder()
                    .paymentId(payment.getPaymentId())
                    .orderId(payment.getOrder().getId())
                    .merchantId(payment.getMerchant().getId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .status(payment.getStatus().name())
                    .paymentMethod(payment.getPaymentMethod())
                    .gatewayPaymentId(payment.getGatewayPaymentId())
                    .failureReason(payment.getFailureReason())
                    .build())
                .build();

            // Serialize to JSON
            String payload = objectMapper.writeValueAsString(event);

            // Store in outbox (same transaction as payment update)
            OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("PAYMENT")
                .aggregateId(payment.getPaymentId())
                .eventType(eventType.name())
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .build();

            outboxEventRepository.save(outboxEvent);

            log.info("Payment event created: event_id={}, event_type={}, payment_id={}", 
                eventId, eventType, payment.getPaymentId());

        } catch (Exception e) {
            log.error("Failed to create payment event: payment_id={}, event_type={}", 
                payment.getPaymentId(), eventType, e);
            // Throwing exception will rollback the entire transaction (payment + event)
            throw new RuntimeException("Failed to create payment event", e);
        }
    }
}
