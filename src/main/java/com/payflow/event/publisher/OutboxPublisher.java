package com.payflow.event.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.observability.BusinessMetrics;
import com.payflow.event.domain.PaymentEvent;
import com.payflow.event.outbox.OutboxEvent;
import com.payflow.event.outbox.OutboxEventRepository;
import com.payflow.event.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outbox Publisher.
 * 
 * Periodically polls pending outbox events and publishes them to Kafka.
 * Handles retry logic and failure tracking.
 * 
 * Flow:
 * 1. Find pending/failed events
 * 2. Publish to Kafka
 * 3. Mark as published on Kafka acknowledgment
 * 4. Retry failed events (up to max retry count)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final BusinessMetrics metrics;

    @Value("${payflow.events.topic:payflow.payment.events}")
    private String topic;

    @Value("${payflow.events.outbox.max-retry-count:5}")
    private int maxRetryCount;

    /**
     * Publishes pending outbox events to Kafka.
     * Runs every 5 seconds by default (configured in application.yml).
     */
    @Scheduled(fixedDelayString = "${payflow.events.outbox.publish-interval-ms:5000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents();
        
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Publishing {} pending outbox events", pendingEvents.size());

        for (OutboxEvent outboxEvent : pendingEvents) {
            publishEvent(outboxEvent);
        }

        // Also retry failed events
        List<OutboxEvent> failedEvents = outboxEventRepository.findFailedEventsForRetry();
        for (OutboxEvent failedEvent : failedEvents) {
            log.info("Retrying failed event: event_id={}, retry_count={}", 
                failedEvent.getEventId(), failedEvent.getRetryCount());
            publishEvent(failedEvent);
        }
    }

    /**
     * Publishes a single outbox event to Kafka.
     */
    private void publishEvent(OutboxEvent outboxEvent) {
        try {
            // Deserialize payload to PaymentEvent
            PaymentEvent event = objectMapper.readValue(outboxEvent.getPayload(), PaymentEvent.class);

            // Kafka key = aggregateId (paymentId) for ordering within partition
            String key = outboxEvent.getAggregateId();

            // Send to Kafka
            CompletableFuture<SendResult<String, PaymentEvent>> future = 
                kafkaTemplate.send(topic, key, event);

            // Handle success/failure asynchronously
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    markAsPublished(outboxEvent);
                    log.info("Event published: event_id={}, topic={}, partition={}, offset={}", 
                        outboxEvent.getEventId(), topic, 
                        result.getRecordMetadata().partition(), 
                        result.getRecordMetadata().offset());
                    metrics.incrementOutboxEventsPublished(outboxEvent.getEventType());
                } else {
                    markAsFailed(outboxEvent, ex.getMessage());
                    log.error("Event publication failed: event_id={}, error={}", 
                        outboxEvent.getEventId(), ex.getMessage(), ex);
                    metrics.incrementOutboxEventsFailed(outboxEvent.getEventType());
                }
            });

        } catch (Exception e) {
            markAsFailed(outboxEvent, e.getMessage());
            log.error("Failed to publish event: event_id={}", outboxEvent.getEventId(), e);
            metrics.incrementOutboxEventsFailed(outboxEvent.getEventType());
        }
    }

    /**
     * Marks event as published.
     */
    @Transactional
    protected void markAsPublished(OutboxEvent outboxEvent) {
        outboxEvent.setStatus(OutboxStatus.PUBLISHED);
        outboxEvent.setPublishedAt(LocalDateTime.now());
        outboxEvent.setLastError(null);
        outboxEventRepository.save(outboxEvent);
    }

    /**
     * Marks event as failed and increments retry count.
     */
    @Transactional
    protected void markAsFailed(OutboxEvent outboxEvent, String error) {
        outboxEvent.setStatus(OutboxStatus.FAILED);
        outboxEvent.setRetryCount(outboxEvent.getRetryCount() + 1);
        outboxEvent.setLastError(error);
        outboxEventRepository.save(outboxEvent);
    }
}
