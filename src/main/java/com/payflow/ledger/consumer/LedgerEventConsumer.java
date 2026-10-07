package com.payflow.ledger.consumer;

import com.payflow.event.consumer.ProcessedEvent;
import com.payflow.event.consumer.ProcessedEventRepository;
import com.payflow.event.domain.PaymentEvent;
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
 * Ledger Event Consumer.
 * 
 * Consumes PAYMENT_CAPTURED events and creates ledger transactions.
 * Idempotent processing prevents duplicate ledger entries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerEventConsumer {

    private static final String CONSUMER_NAME = "LedgerEventConsumer";
    
    private final LedgerService ledgerService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(
        topics = "${payflow.events.topic:payflow.payment.events}",
        groupId = "payflow-ledger-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(
            @Payload PaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Ledger consumer received: event_id={}, event_type={}, payment_id={}", 
            event.getEventId(), event.getEventType(), event.getAggregateId());

        try {
            // Only process PAYMENT_CAPTURED events
            if (!"PAYMENT_CAPTURED".equals(event.getEventType())) {
                log.debug("Skipping non-capture event: event_type={}", event.getEventType());
                acknowledgment.acknowledge();
                return;
            }

            // Idempotency check
            if (isAlreadyProcessed(event.getEventId())) {
                log.info("Event already processed by ledger consumer: event_id={}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }

            // Create ledger transaction
            ledgerService.recordPaymentCapture(
                event.getData().getPaymentId(),
                event.getData().getMerchantId(),
                event.getData().getAmount(),
                event.getData().getCurrency()
            );

            // Mark as processed
            markAsProcessed(event.getEventId());

            acknowledgment.acknowledge();

            log.info("Ledger transaction created: event_id={}, payment_id={}, amount={} {}", 
                event.getEventId(), 
                event.getData().getPaymentId(),
                event.getData().getAmount(),
                event.getData().getCurrency());

        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate ledger event processing detected: event_id={}", event.getEventId());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process ledger event: event_id={}, payment_id={}", 
                event.getEventId(), event.getData().getPaymentId(), e);
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
