package com.payflow.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.event.domain.DisputeEvent;
import com.payflow.event.domain.DisputeEventType;
import com.payflow.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispute Event Consumer.
 * 
 * Consumes dispute events from Kafka and:
 * - Creates compensating ledger transactions for DISPUTE_LOST
 * - Creates audit records for DISPUTE_WON
 * - Handles idempotency via processed_events table
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DisputeEventConsumer {

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final LedgerService ledgerService;

    @KafkaListener(
        topics = "${kafka.topic.dispute-events}",
        groupId = "${kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(String message) {
        try {
            log.info("Consuming dispute event from Kafka");

            // Parse event
            DisputeEvent event = objectMapper.readValue(message, DisputeEvent.class);
            String eventId = event.getEventId();
            DisputeEventType eventType = DisputeEventType.valueOf(event.getEventType());

            log.info("Processing dispute event: event_id={}, event_type={}, dispute_id={}", 
                eventId, eventType, event.getData().getDisputeId());

            // Idempotency check
            if (isProcessed(eventId)) {
                log.info("Dispute event already processed: event_id={}", eventId);
                return;
            }

            // Process based on event type
            switch (eventType) {
                case DISPUTE_LOST:
                    handleDisputeLost(event);
                    break;
                case DISPUTE_WON:
                    handleDisputeWon(event);
                    break;
                case DISPUTE_CREATED:
                case DISPUTE_EVIDENCE_REQUIRED:
                case DISPUTE_EVIDENCE_SUBMITTED:
                case DISPUTE_CLOSED:
                    // No ledger impact for these events
                    log.info("No ledger action required for event type: {}", eventType);
                    break;
                default:
                    log.warn("Unknown dispute event type: {}", eventType);
            }

            // Mark as processed
            markAsProcessed(eventId, event.getAggregateType(), event.getAggregateId(), event.getEventType());

            log.info("Dispute event processed successfully: event_id={}", eventId);

        } catch (DataIntegrityViolationException e) {
            // Duplicate event (already processed) - safe to ignore
            log.info("Duplicate dispute event detected, already processed");
        } catch (Exception e) {
            log.error("Failed to process dispute event", e);
            throw new RuntimeException("Failed to process dispute event", e);
        }
    }

    private void handleDisputeLost(DisputeEvent event) {
        DisputeEvent.DisputeEventData data = event.getData();

        log.info("Creating compensating ledger transaction for lost dispute: dispute_id={}", 
            data.getDisputeId());

        // Create compensating transaction
        ledgerService.recordDisputeLoss(
            data.getDisputeId(),
            data.getPaymentId(),
            data.getMerchantId(),
            data.getAmount(),
            data.getCurrency()
        );

        log.info("Dispute loss ledger transaction created: dispute_id={}", data.getDisputeId());
    }

    private void handleDisputeWon(DisputeEvent event) {
        DisputeEvent.DisputeEventData data = event.getData();

        log.info("Creating audit record for won dispute: dispute_id={}", data.getDisputeId());

        // Create audit record (no financial impact)
        ledgerService.recordDisputeWin(
            data.getDisputeId(),
            data.getPaymentId(),
            data.getMerchantId(),
            data.getAmount(),
            data.getCurrency()
        );

        log.info("Dispute win audit record created: dispute_id={}", data.getDisputeId());
    }

    private boolean isProcessed(String eventId) {
        return processedEventRepository.existsByEventIdAndConsumerName(eventId, "DisputeEventConsumer");
    }

    private void markAsProcessed(String eventId, String aggregateType, String aggregateId, String eventType) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
            .eventId(eventId)
            .consumerName("DisputeEventConsumer")
            .build();

        processedEventRepository.save(processedEvent);
    }
}
