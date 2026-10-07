package com.payflow.ledger.consumer;

import com.payflow.event.consumer.ProcessedEvent;
import com.payflow.event.consumer.ProcessedEventRepository;
import com.payflow.event.domain.PaymentEvent;
import com.payflow.ledger.entity.LedgerTransaction;
import com.payflow.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerEventConsumerTest {

    @Mock
    private LedgerService ledgerService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private LedgerEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LedgerEventConsumer(ledgerService, processedEventRepository);
    }

    @Test
    void consume_PaymentCaptured_CreatesLedgerTransaction() {
        // Arrange
        PaymentEvent event = createPaymentEvent("EVT_123", "PAYMENT_CAPTURED", "PAY_123");
        
        when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
        when(ledgerService.recordPaymentCapture(any(), any(), any(), any()))
            .thenReturn(LedgerTransaction.builder().id(1L).build());

        // Act
        consumer.consume(event, "test-topic", 0, 100L, acknowledgment);

        // Assert
        verify(ledgerService).recordPaymentCapture(
            eq("PAY_123"),
            eq(1L),
            eq(new BigDecimal("1000.00")),
            eq("INR")
        );
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_NonCaptureEvent_Skipped() {
        // Arrange
        PaymentEvent event = createPaymentEvent("EVT_123", "PAYMENT_CREATED", "PAY_123");

        // Act
        consumer.consume(event, "test-topic", 0, 100L, acknowledgment);

        // Assert
        verify(ledgerService, never()).recordPaymentCapture(any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_AlreadyProcessed_Skipped() {
        // Arrange
        PaymentEvent event = createPaymentEvent("EVT_123", "PAYMENT_CAPTURED", "PAY_123");
        when(processedEventRepository.existsByEventIdAndConsumerName("EVT_123", "LedgerEventConsumer"))
            .thenReturn(true);

        // Act
        consumer.consume(event, "test-topic", 0, 100L, acknowledgment);

        // Assert
        verify(ledgerService, never()).recordPaymentCapture(any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_DuplicateConstraintViolation_Acknowledged() {
        // Arrange
        PaymentEvent event = createPaymentEvent("EVT_123", "PAYMENT_CAPTURED", "PAY_123");
        
        when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
        when(processedEventRepository.save(any())).thenThrow(new DataIntegrityViolationException("Duplicate"));

        // Act
        consumer.consume(event, "test-topic", 0, 100L, acknowledgment);

        // Assert
        verify(acknowledgment).acknowledge();
    }

    private PaymentEvent createPaymentEvent(String eventId, String eventType, String paymentId) {
        PaymentEvent.PaymentEventData data = PaymentEvent.PaymentEventData.builder()
            .paymentId(paymentId)
            .merchantId(1L)
            .orderId(1L)
            .amount(new BigDecimal("1000.00"))
            .currency("INR")
            .status("CAPTURED")
            .build();

        return PaymentEvent.builder()
            .eventId(eventId)
            .eventType(eventType)
            .aggregateType("PAYMENT")
            .aggregateId(paymentId)
            .merchantId(1L)
            .occurredAt(LocalDateTime.now())
            .version(1)
            .data(data)
            .build();
    }
}
