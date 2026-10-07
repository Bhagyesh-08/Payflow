package com.payflow.refund.consumer;

import com.payflow.event.consumer.ProcessedEvent;
import com.payflow.event.consumer.ProcessedEventRepository;
import com.payflow.event.domain.RefundEvent;
import com.payflow.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refund Event Consumer.
 * 
 * Consumes REFUND_SUCCEEDED events and creates compensating ledger transactions.
 * Idempotent processing prevents duplicate ledger entries.
 * 
 * Critical: Refunds create NEW compensating transactions.
 * Original payment ledger transactions remain unchanged.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundEventConsumer {

    private static final String CONSUMER_NAME = "RefundEventConsumer";
    
    private final LedgerService ledgerService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(
        topics = "${payflow.events.topic:payflow.payment.events}",
        groupId = "payflow-refund-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(
            @Payload RefundEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Refund consumer received: event_id={}, event_type={}, refund_id={}", 
            event.getEventId(), event.getEventType(), event.getAggregateId());

        try {
            // Only process REFUND_SUCCEEDED events
            if (!"REFUND_SUCCEEDED".equals(event.getEventType())) {
                log.debug("Skipping non-success refund event: event_type={}", event.getEventType());
                acknowledgment.acknowledge();
                return;
            }

            // Idempotency check
            if (isAlreadyProcessed(event.getEventId())) {
                log.info("Event already processed by refund consumer: event_id={}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }

            // Create compensating ledger transaction
            // Original payment ledger transaction remains unchanged
            ledgerService.recordRefund(
                event.getData().getRefundId(),
                event.getData().getPaymentId(),
                event.getData().getMerchantId(),
                event.getData().getAmount(),
                event.getData().getCurrency()
            );

            // Mark as processed
            markAsProcessed(event.getEventId());

            acknowledgment.acknowledge();

            log.info("Refund ledger transaction created: event_id={}, refund_id={}, amount={} {}", 
                event.getEventId(), 
                event.getData().getRefundId(),
                event.getData().getAmount(),
                event.getData().getCurrency());

        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate refund event processing detected: event_id={}", event.getEventId());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process refund event: event_id={}, refund_id={}", 
                event.getEventId(), event.getData().getRefundId(), e);
            throw e;
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME);
    }

    private void markAsProcessed(String eventId) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
            .eventId(eventId)
            .consumerName(CONSUMER_NAME)
            .build();
        
        processedEventRepository.save(processedEvent);
    }
}
