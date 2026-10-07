package com.payflow.refund.consumer;

import com.payflow.event.consumer.ProcessedEvent;
import com.payflow.event.consumer.ProcessedEventRepository;
import com.payflow.event.domain.RefundEvent;
import com.payflow.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class RefundEventConsumerTest {

    @Mock
    private LedgerService ledgerService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private RefundEventConsumer refundEventConsumer;

    private RefundEvent refundSucceededEvent;

    @BeforeEach
    void setUp() {
        refundSucceededEvent = RefundEvent.builder()
            .eventId("EVT_123")
            .eventType("REFUND_SUCCEEDED")
            .occurredAt(LocalDateTime.now())
            .aggregateType("REFUND")
            .aggregateId("REF_123")
            .merchantId(1L)
            .version(1)
            .data(RefundEvent.RefundEventData.builder()
                .refundId("REF_123")
                .paymentId("PAY_123")
                .merchantId(1L)
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .status("SUCCEEDED")
                .gateway("RAZORPAY")
                .gatewayRefundId("gw_ref_123")
                .build())
            .build();
    }

    @Test
    void consume_RefundSucceeded_CreatesLedgerTransaction() {
        // Arrange
        when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        refundEventConsumer.consume(refundSucceededEvent, "payflow.payment.events", 0, 100L, acknowledgment);

        // Assert
        verify(ledgerService).recordRefund(
            eq("REF_123"),
            eq("PAY_123"),
            eq(1L),
            eq(new BigDecimal("500.00")),
            eq("INR")
        );
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_DuplicateEvent_SkipsProcessing() {
        // Arrange
        when(processedEventRepository.existsByEventIdAndConsumerName("EVT_123", "RefundEventConsumer"))
            .thenReturn(true);

        // Act
        refundEventConsumer.consume(refundSucceededEvent, "payflow.payment.events", 0, 100L, acknowledgment);

        // Assert
        verify(ledgerService, never()).recordRefund(any(), any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_RefundCreated_SkipsLedgerCreation() {
        // Arrange
        RefundEvent refundCreatedEvent = RefundEvent.builder()
            .eventId("EVT_456")
            .eventType("REFUND_CREATED")
            .aggregateId("REF_456")
            .data(RefundEvent.RefundEventData.builder()
                .refundId("REF_456")
                .build())
            .build();

        // Act
        refundEventConsumer.consume(refundCreatedEvent, "payflow.payment.events", 0, 101L, acknowledgment);

        // Assert
        verify(ledgerService, never()).recordRefund(any(), any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_RefundFailed_SkipsLedgerCreation() {
        // Arrange
        RefundEvent refundFailedEvent = RefundEvent.builder()
            .eventId("EVT_789")
            .eventType("REFUND_FAILED")
            .aggregateId("REF_789")
            .data(RefundEvent.RefundEventData.builder()
                .refundId("REF_789")
                .failureReason("Insufficient balance")
                .build())
            .build();

        // Act
        refundEventConsumer.consume(refundFailedEvent, "payflow.payment.events", 0, 102L, acknowledgment);

        // Assert
        verify(ledgerService, never()).recordRefund(any(), any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_ConcurrentDuplicate_AcknowledgesSafely() {
        // Arrange
        when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
        when(processedEventRepository.save(any(ProcessedEvent.class)))
            .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        // Act
        refundEventConsumer.consume(refundSucceededEvent, "payflow.payment.events", 0, 100L, acknowledgment);

        // Assert
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_ProcessingFailure_DoesNotAcknowledge() {
        // Arrange
        when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
        doThrow(new RuntimeException("Database error")).when(ledgerService)
            .recordRefund(any(), any(), any(), any(), any());

        // Act & Assert
        try {
            refundEventConsumer.consume(refundSucceededEvent, "payflow.payment.events", 0, 100L, acknowledgment);
        } catch (RuntimeException e) {
            // Expected
        }

        verify(acknowledgment, never()).acknowledge();
    }
}
