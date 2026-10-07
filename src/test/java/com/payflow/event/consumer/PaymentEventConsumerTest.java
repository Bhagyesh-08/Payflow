package com.payflow.event.consumer;

import com.payflow.event.domain.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private PaymentEventConsumer paymentEventConsumer;

    @Captor
    private ArgumentCaptor<ProcessedEvent> processedEventCaptor;

    @Test
    void consume_NewEvent_ProcessesAndAcknowledges() {
        // Arrange
        PaymentEvent event = createPaymentEvent("EVT_123");
        
        when(processedEventRepository.existsByEventIdAndConsumerName("EVT_123", "PaymentEventConsumer"))
            .thenReturn(false);
        when(processedEventRepository.save(any(ProcessedEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        paymentEventConsumer.consume(event, "payflow.payment.events", 0, 100L, acknowledgment);

        // Assert
        verify(processedEventRepository).save(processedEventCaptor.capture());
        ProcessedEvent saved = processedEventCaptor.getValue();
        assertThat(saved.getEventId()).isEqualTo("EVT_123");
        assertThat(saved.getConsumerName()).isEqualTo("PaymentEventConsumer");
        
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_DuplicateEvent_SkipsProcessing() {
        // Arrange
        PaymentEvent event = createPaymentEvent("EVT_123");
        
        when(processedEventRepository.existsByEventIdAndConsumerName("EVT_123", "PaymentEventConsumer"))
            .thenReturn(true);

        // Act
        paymentEventConsumer.consume(event, "payflow.payment.events", 0, 100L, acknowledgment);

        // Assert
        verify(processedEventRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_ConcurrentProcessing_HandlesDataIntegrityViolation() {
        // Arrange
        PaymentEvent event = createPaymentEvent("EVT_123");
        
        when(processedEventRepository.existsByEventIdAndConsumerName("EVT_123", "PaymentEventConsumer"))
            .thenReturn(false);
        when(processedEventRepository.save(any(ProcessedEvent.class)))
            .thenThrow(new DataIntegrityViolationException("Duplicate"));

        // Act
        paymentEventConsumer.consume(event, "payflow.payment.events", 0, 100L, acknowledgment);

        // Assert
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_ProcessingFailure_DoesNotAcknowledge() {
        // Arrange
        PaymentEvent event = createPaymentEvent("EVT_123");
        
        when(processedEventRepository.existsByEventIdAndConsumerName("EVT_123", "PaymentEventConsumer"))
            .thenReturn(false);
        when(processedEventRepository.save(any(ProcessedEvent.class)))
            .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThatThrownBy(() -> 
            paymentEventConsumer.consume(event, "payflow.payment.events", 0, 100L, acknowledgment))
            .isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consume_MultipleEvents_ProcessesEach() {
        // Arrange
        PaymentEvent event1 = createPaymentEvent("EVT_123");
        PaymentEvent event2 = createPaymentEvent("EVT_456");
        
        when(processedEventRepository.existsByEventIdAndConsumerName(anyString(), eq("PaymentEventConsumer")))
            .thenReturn(false);
        when(processedEventRepository.save(any(ProcessedEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        paymentEventConsumer.consume(event1, "payflow.payment.events", 0, 100L, acknowledgment);
        paymentEventConsumer.consume(event2, "payflow.payment.events", 0, 101L, acknowledgment);

        // Assert
        verify(processedEventRepository, times(2)).save(any(ProcessedEvent.class));
        verify(acknowledgment, times(2)).acknowledge();
    }

    private PaymentEvent createPaymentEvent(String eventId) {
        return PaymentEvent.builder()
            .eventId(eventId)
            .eventType("PAYMENT_CREATED")
            .aggregateType("PAYMENT")
            .aggregateId("PAY_TEST")
            .merchantId(1L)
            .occurredAt(LocalDateTime.now())
            .version(1)
            .data(PaymentEvent.PaymentEventData.builder()
                .paymentId("PAY_TEST")
                .orderId(1L)
                .merchantId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .status("CREATED")
                .build())
            .build();
    }
}
