package com.payflow.event.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.observability.BusinessMetrics;
import com.payflow.event.domain.PaymentEvent;
import com.payflow.event.outbox.OutboxEvent;
import com.payflow.event.outbox.OutboxEventRepository;
import com.payflow.event.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BusinessMetrics metrics;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate, objectMapper, metrics);
        ReflectionTestUtils.setField(outboxPublisher, "topic", "payflow.payment.events");
        ReflectionTestUtils.setField(outboxPublisher, "maxRetryCount", 5);
    }

    @Test
    void publishPendingEvents_NoPendingEvents_DoesNothing() {
        // Arrange
        when(outboxEventRepository.findPendingEvents()).thenReturn(Collections.emptyList());

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publishPendingEvents_WithPendingEvent_PublishesToKafka() throws Exception {
        // Arrange
        OutboxEvent pendingEvent = createOutboxEvent(OutboxStatus.PENDING);
        PaymentEvent paymentEvent = createPaymentEvent();
        
        when(outboxEventRepository.findPendingEvents()).thenReturn(Arrays.asList(pendingEvent));
        when(outboxEventRepository.findFailedEventsForRetry()).thenReturn(Collections.emptyList());
        when(objectMapper.readValue(anyString(), eq(PaymentEvent.class))).thenReturn(paymentEvent);
        
        CompletableFuture<SendResult<String, PaymentEvent>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), any(PaymentEvent.class))).thenReturn(future);

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(kafkaTemplate).send("payflow.payment.events", "PAY_123", paymentEvent);
    }

    @Test
    void markAsPublished_UpdatesEventStatus() {
        // Arrange
        OutboxEvent event = createOutboxEvent(OutboxStatus.PENDING);
        when(outboxEventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        outboxPublisher.markAsPublished(event);

        // Assert
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEvent savedEvent = outboxEventCaptor.getValue();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(savedEvent.getPublishedAt()).isNotNull();
        assertThat(savedEvent.getLastError()).isNull();
    }

    @Test
    void markAsFailed_UpdatesEventStatusAndIncrementsRetry() {
        // Arrange
        OutboxEvent event = createOutboxEvent(OutboxStatus.PENDING);
        event.setRetryCount(0);
        when(outboxEventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        outboxPublisher.markAsFailed(event, "Kafka timeout");

        // Assert
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEvent savedEvent = outboxEventCaptor.getValue();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(savedEvent.getRetryCount()).isEqualTo(1);
        assertThat(savedEvent.getLastError()).isEqualTo("Kafka timeout");
    }

    private OutboxEvent createOutboxEvent(OutboxStatus status) {
        return OutboxEvent.builder()
            .id(1L)
            .eventId("EVT_123")
            .aggregateType("PAYMENT")
            .aggregateId("PAY_123")
            .eventType("PAYMENT_CREATED")
            .payload("{\"eventId\":\"EVT_123\"}")
            .status(status)
            .retryCount(0)
            .build();
    }

    private PaymentEvent createPaymentEvent() {
        return PaymentEvent.builder()
            .eventId("EVT_123")
            .eventType("PAYMENT_CREATED")
            .aggregateType("PAYMENT")
            .aggregateId("PAY_123")
            .occurredAt(LocalDateTime.now())
            .version(1)
            .build();
    }
}
