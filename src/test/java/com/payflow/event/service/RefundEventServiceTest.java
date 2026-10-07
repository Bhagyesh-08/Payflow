package com.payflow.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.event.domain.RefundEventType;
import com.payflow.event.outbox.OutboxEvent;
import com.payflow.event.outbox.OutboxEventRepository;
import com.payflow.event.outbox.OutboxStatus;
import com.payflow.merchant.entity.Merchant;
import com.payflow.payment.entity.Payment;
import com.payflow.refund.entity.Refund;
import com.payflow.refund.enums.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RefundEventService refundEventService;

    private Refund refund;

    @BeforeEach
    void setUp() {
        Merchant merchant = Merchant.builder()
            .id(1L)
            .businessName("Test Merchant")
            .build();

        Payment payment = Payment.builder()
            .id(1L)
            .paymentId("PAY_123")
            .merchant(merchant)
            .build();

        refund = Refund.builder()
            .id(1L)
            .refundId("REF_123")
            .payment(payment)
            .merchant(merchant)
            .amount(new BigDecimal("500.00"))
            .currency("INR")
            .status(RefundStatus.REQUESTED)
            .gateway("RAZORPAY")
            .reason("Customer request")
            .build();
    }

    @Test
    void createRefundEvent_RefundCreated_StoresInOutbox() throws Exception {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":\"EVT_123\"}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        refundEventService.createRefundEvent(refund, RefundEventType.REFUND_CREATED);

        // Assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("REFUND");
        assertThat(saved.getAggregateId()).isEqualTo("REF_123");
        assertThat(saved.getEventType()).isEqualTo("REFUND_CREATED");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void createRefundEvent_RefundSucceeded_StoresInOutbox() throws Exception {
        // Arrange
        refund.setStatus(RefundStatus.SUCCEEDED);
        refund.setGatewayRefundId("gw_ref_123");
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":\"EVT_456\"}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        refundEventService.createRefundEvent(refund, RefundEventType.REFUND_SUCCEEDED);

        // Assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("REFUND_SUCCEEDED");
    }

    @Test
    void createRefundEvent_RefundFailed_StoresInOutbox() throws Exception {
        // Arrange
        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason("Insufficient balance");
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":\"EVT_789\"}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        refundEventService.createRefundEvent(refund, RefundEventType.REFUND_FAILED);

        // Assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("REFUND_FAILED");
    }
}
