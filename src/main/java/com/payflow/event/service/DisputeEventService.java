package com.payflow.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.util.IdGenerator;
import com.payflow.dispute.entity.Dispute;
import com.payflow.event.domain.DisputeEvent;
import com.payflow.event.domain.DisputeEventType;
import com.payflow.event.outbox.OutboxEvent;
import com.payflow.event.outbox.OutboxEventRepository;
import com.payflow.event.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Dispute Event Service.
 * 
 * Creates domain events and stores them in the transactional outbox.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createDisputeEvent(Dispute dispute, DisputeEventType eventType) {
        try {
            String eventId = IdGenerator.generateEventId();

            DisputeEvent event = DisputeEvent.builder()
                .eventId(eventId)
                .eventType(eventType.name())
                .occurredAt(LocalDateTime.now())
                .aggregateType("DISPUTE")
                .aggregateId(dispute.getDisputeId())
                .merchantId(dispute.getMerchant().getId())
                .version(1)
                .data(DisputeEvent.DisputeEventData.builder()
                    .disputeId(dispute.getDisputeId())
                    .paymentId(dispute.getPayment().getPaymentId())
                    .merchantId(dispute.getMerchant().getId())
                    .amount(dispute.getAmount())
                    .currency(dispute.getCurrency())
                    .status(dispute.getStatus().name())
                    .gateway(dispute.getGateway())
                    .gatewayDisputeId(dispute.getGatewayDisputeId())
                    .reason(dispute.getReason())
                    .resolutionReason(dispute.getResolutionReason())
                    .build())
                .build();

            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("DISPUTE")
                .aggregateId(dispute.getDisputeId())
                .eventType(eventType.name())
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .build();

            outboxEventRepository.save(outboxEvent);

            log.info("Dispute event created: event_id={}, event_type={}, dispute_id={}", 
                eventId, eventType, dispute.getDisputeId());

        } catch (Exception e) {
            log.error("Failed to create dispute event: dispute_id={}, event_type={}", 
                dispute.getDisputeId(), eventType, e);
            throw new RuntimeException("Failed to create dispute event", e);
        }
    }
}
