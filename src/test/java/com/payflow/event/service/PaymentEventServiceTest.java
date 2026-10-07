package com.payflow.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.event.domain.PaymentEventType;
import com.payflow.event.outbox.OutboxEvent;
import com.payflow.event.outbox.OutboxEventRepository;
import com.payflow.event.outbox.OutboxStatus;
import com.payflow.merchant.entity.Merchant;
import com.payflow.order.entity.Order;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentEventService paymentEventService;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    @Test
    void createPaymentEvent_Success() throws Exception {
        // Arrange
        Payment payment = createTestPayment();
        
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":\"EVT_123\"}");
        when(outboxEventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        paymentEventService.createPaymentEvent(payment, PaymentEventType.PAYMENT_CREATED);

        // Assert
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        
        OutboxEvent savedEvent = outboxEventCaptor.getValue();
        assertThat(savedEvent.getEventId()).isNotNull();
        assertThat(savedEvent.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(savedEvent.getAggregateId()).isEqualTo("PAY_TEST");
        assertThat(savedEvent.getEventType()).isEqualTo("PAYMENT_CREATED");
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedEvent.getPayload()).isNotNull();
    }

    @Test
    void createPaymentEvent_DifferentEventTypes_CreatesCorrectEvents() throws Exception {
        // Arrange
        Payment payment = createTestPayment();
        
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act & Assert - PAYMENT_CAPTURED
        paymentEventService.createPaymentEvent(payment, PaymentEventType.PAYMENT_CAPTURED);
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        assertThat(outboxEventCaptor.getValue().getEventType()).isEqualTo("PAYMENT_CAPTURED");
    }

    private Payment createTestPayment() {
        Merchant merchant = Merchant.builder()
            .id(1L)
            .businessName("Test Merchant")
            .build();

        Order order = Order.builder()
            .id(1L)
            .merchant(merchant)
            .build();

        return Payment.builder()
            .id(1L)
            .paymentId("PAY_TEST")
            .order(order)
            .merchant(merchant)
            .amount(new BigDecimal("100.00"))
            .currency("INR")
            .status(PaymentStatus.CREATED)
            .build();
    }
}
