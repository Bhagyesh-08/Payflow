package com.payflow.event.consumer;

import com.payflow.common.observability.BusinessMetrics;
import com.payflow.event.domain.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment Event Consumer.
 * 
 * Consumes payment events from Kafka with idempotent processing.
 * Uses database-backed deduplication to prevent duplicate business operations.
 * 
 * Flow:
 * 1. Receive event from Kafka
 * 2. Check if already processed (database lookup)
 * 3. If duplicate → acknowledge and skip
 * 4. If new → process business logic
 * 5. Mark as processed (database insert with unique constraint)
 * 6. Acknowledge to Kafka
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private static final String CONSUMER_NAME = "PaymentEventConsumer";
    
    private final ProcessedEventRepository processedEventRepository;
    private final BusinessMetrics metrics;

    @KafkaListener(
        topics = "${payflow.events.topic:payflow.payment.events}",
        groupId = "${spring.kafka.consumer.group-id:payflow-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(
            @Payload PaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        long startTime = System.currentTimeMillis();
        MDC.put("eventId", event.getEventId());
        MDC.put("eventType", event.getEventType().toString());
        
        log.info("Received event: event_id={}, event_type={}, aggregate_id={}, topic={}, partition={}, offset={}", 
            event.getEventId(), event.getEventType(), event.getAggregateId(), topic, partition, offset);

        try {
            metrics.incrementEventsConsumed(event.getEventType().toString());
            
            // Check if already processed
            if (isAlreadyProcessed(event.getEventId())) {
                log.info("Event already processed, skipping: event_id={}", event.getEventId());
                metrics.incrementDuplicateEvents(CONSUMER_NAME);
                acknowledgment.acknowledge();
                return;
            }

            // Process business logic
            processEvent(event);

            // Mark as processed (with unique constraint for race safety)
            markAsProcessed(event.getEventId());

            // Acknowledge to Kafka
            acknowledgment.acknowledge();

            long duration = System.currentTimeMillis() - startTime;
            metrics.recordEventProcessingLatency(CONSUMER_NAME, duration);
            
            log.info("Event processed successfully: event_id={}, event_type={}, duration_ms={}", 
                event.getEventId(), event.getEventType(), duration);

        } catch (DataIntegrityViolationException e) {
            // Another consumer instance processed this event concurrently
            log.info("Event already processed by another consumer: event_id={}", event.getEventId());
            metrics.incrementDuplicateEvents(CONSUMER_NAME);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process event: event_id={}, event_type={}", 
                event.getEventId(), event.getEventType(), e);
            metrics.incrementEventProcessingErrors(CONSUMER_NAME);
            // Do NOT acknowledge - message will be retried
            // Kafka will redeliver based on consumer configuration
            throw e;
        }
    }

    /**
     * Checks if event was already processed by this consumer.
     */
    private boolean isAlreadyProcessed(String eventId) {
        return processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME);
    }

    /**
     * Marks event as processed.
     * Uses unique constraint (event_id, consumer_name) for race safety.
     */
    private void markAsProcessed(String eventId) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
            .eventId(eventId)
            .consumerName(CONSUMER_NAME)
            .build();
        
        processedEventRepository.save(processedEvent);
    }

    /**
     * Business logic for processing payment events.
     * 
     * Phase 4: Simple audit logging.
     * Future phases: Ledger updates, reconciliation, notifications, etc.
     */
    private void processEvent(PaymentEvent event) {
        // Phase 4: Log event for audit
        log.info("Processing payment event: event_type={}, payment_id={}, merchant_id={}, amount={}, status={}", 
            event.getEventType(), 
            event.getData().getPaymentId(),
            event.getData().getMerchantId(),
            event.getData().getAmount(),
            event.getData().getStatus());

        // Future phases will add:
        // - Ledger entries (double-entry accounting)
        // - Reconciliation records
        // - Notification triggers
        // - Analytics updates
        // - External system integrations
    }
}
