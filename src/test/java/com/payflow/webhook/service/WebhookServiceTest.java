package com.payflow.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.exception.BusinessException;
import com.payflow.common.observability.BusinessMetrics;
import com.payflow.event.service.PaymentEventService;
import com.payflow.merchant.entity.Merchant;
import com.payflow.order.entity.Order;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.enums.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.statemachine.PaymentStateMachine;
import com.payflow.webhook.entity.WebhookEvent;
import com.payflow.webhook.enums.WebhookStatus;
import com.payflow.webhook.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private BusinessMetrics metrics;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentStateMachine stateMachine;

    @Mock
    private WebhookSignatureVerifier signatureVerifier;

    @Mock
    private PaymentEventService paymentEventService;

    @Mock
    private com.payflow.refund.repository.RefundRepository refundRepository;

    @Mock
    private com.payflow.dispute.repository.DisputeRepository disputeRepository;

    @Mock
    private com.payflow.event.service.RefundEventService refundEventService;

    @Mock
    private com.payflow.event.service.DisputeEventService disputeEventService;

    @Captor
    private ArgumentCaptor<WebhookEvent> webhookEventCaptor;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private WebhookService webhookService;
    private ObjectMapper objectMapper;

    private static final String VALID_PAYLOAD = 
        "{\"id\":\"evt_123\",\"event\":\"payment.captured\"," +
        "\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";
    private static final String SIGNATURE = "valid_signature";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webhookService = new WebhookService(
            metrics, webhookEventRepository, paymentRepository, refundRepository, disputeRepository,
            stateMachine, signatureVerifier, objectMapper, paymentEventService, 
            refundEventService, disputeEventService);
    }

    @Test
    void processWebhook_ValidPaymentCaptured_Success() {
        // Arrange
        Payment payment = createTestPayment(PaymentStatus.AUTHORIZED);
        
        when(webhookEventRepository.save(any(WebhookEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByGatewayPaymentId("pay_123"))
            .thenReturn(Optional.of(payment));
        doNothing().when(stateMachine).validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED);

        // Act
        webhookService.processWebhook(VALID_PAYLOAD, SIGNATURE);

        // Assert
        verify(signatureVerifier).verifySignature(VALID_PAYLOAD, SIGNATURE);
        verify(webhookEventRepository, atLeastOnce()).save(webhookEventCaptor.capture());
        
        WebhookEvent savedEvent = webhookEventCaptor.getValue();
        assertThat(savedEvent.getEventId()).isEqualTo("evt_123");
        assertThat(savedEvent.getStatus()).isEqualTo(WebhookStatus.PROCESSED);

        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void processWebhook_DuplicateEvent_ThrowsException() {
        // Arrange
        WebhookEvent existingEvent = WebhookEvent.builder()
            .eventId("evt_123")
            .status(WebhookStatus.PROCESSED)
            .build();

        when(webhookEventRepository.save(any(WebhookEvent.class)))
            .thenThrow(new DataIntegrityViolationException("Duplicate"));
        when(webhookEventRepository.findByEventId("evt_123"))
            .thenReturn(Optional.of(existingEvent));

        // Act & Assert
        assertThatThrownBy(() -> webhookService.processWebhook(VALID_PAYLOAD, SIGNATURE))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "WEBHOOK_DUPLICATE");

        verify(signatureVerifier).verifySignature(VALID_PAYLOAD, SIGNATURE);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processWebhook_InvalidSignature_ThrowsException() {
        // Arrange
        doThrow(new BusinessException("WEBHOOK_SIGNATURE_INVALID", "Invalid signature"))
            .when(signatureVerifier).verifySignature(VALID_PAYLOAD, SIGNATURE);

        // Act & Assert
        assertThatThrownBy(() -> webhookService.processWebhook(VALID_PAYLOAD, SIGNATURE))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "WEBHOOK_SIGNATURE_INVALID");

        verify(webhookEventRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processWebhook_PaymentNotFound_IgnoresEvent() {
        // Arrange
        when(webhookEventRepository.save(any(WebhookEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByGatewayPaymentId("pay_123"))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> webhookService.processWebhook(VALID_PAYLOAD, SIGNATURE))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "PAYMENT_NOT_FOUND");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processWebhook_InvalidStateTransition_FailsEvent() {
        // Arrange
        Payment payment = createTestPayment(PaymentStatus.FAILED);
        
        when(webhookEventRepository.save(any(WebhookEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByGatewayPaymentId("pay_123"))
            .thenReturn(Optional.of(payment));
        doThrow(new BusinessException("INVALID_PAYMENT_STATE", "Cannot transition"))
            .when(stateMachine).validateTransition(PaymentStatus.FAILED, PaymentStatus.CAPTURED);

        // Act & Assert
        assertThatThrownBy(() -> webhookService.processWebhook(VALID_PAYLOAD, SIGNATURE))
            .isInstanceOf(BusinessException.class);

        verify(webhookEventRepository, atLeastOnce()).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();
        assertThat(savedEvent.getStatus()).isEqualTo(WebhookStatus.FAILED);
    }

    @Test
    void processWebhook_AlreadyCaptured_IdempotentSuccess() {
        // Arrange
        Payment payment = createTestPayment(PaymentStatus.CAPTURED);
        
        when(webhookEventRepository.save(any(WebhookEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByGatewayPaymentId("pay_123"))
            .thenReturn(Optional.of(payment));

        // Act
        webhookService.processWebhook(VALID_PAYLOAD, SIGNATURE);

        // Assert
        verify(webhookEventRepository, atLeastOnce()).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();
        assertThat(savedEvent.getStatus()).isEqualTo(WebhookStatus.PROCESSED);

        // Payment not updated because already in target state
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processWebhook_PaymentAuthorized_Success() {
        // Arrange
        String authorizedPayload = 
            "{\"id\":\"evt_124\",\"event\":\"payment.authorized\"," +
            "\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_124\"}}}}";
        
        Payment payment = createTestPayment(PaymentStatus.PAYMENT_INITIATED);
        payment.setGatewayPaymentId("pay_124");
        
        when(webhookEventRepository.save(any(WebhookEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByGatewayPaymentId("pay_124"))
            .thenReturn(Optional.of(payment));
        doNothing().when(stateMachine).validateTransition(
            PaymentStatus.PAYMENT_INITIATED, PaymentStatus.AUTHORIZED);

        // Act
        webhookService.processWebhook(authorizedPayload, SIGNATURE);

        // Assert
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void processWebhook_PaymentFailed_Success() {
        // Arrange
        String failedPayload = 
            "{\"id\":\"evt_125\",\"event\":\"payment.failed\"," +
            "\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_125\",\"error_description\":\"Insufficient funds\"}}}}";
        
        Payment payment = createTestPayment(PaymentStatus.PAYMENT_INITIATED);
        payment.setGatewayPaymentId("pay_125");
        
        when(webhookEventRepository.save(any(WebhookEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByGatewayPaymentId("pay_125"))
            .thenReturn(Optional.of(payment));
        when(stateMachine.isTerminalState(PaymentStatus.PAYMENT_INITIATED)).thenReturn(false);

        // Act
        webhookService.processWebhook(failedPayload, SIGNATURE);

        // Assert
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(savedPayment.getFailureReason()).isEqualTo("Insufficient funds");
    }

    @Test
    void processWebhook_UnsupportedEventType_IgnoresEvent() {
        // Arrange
        String unsupportedPayload = 
            "{\"id\":\"evt_126\",\"event\":\"payment.refunded\"," +
            "\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_126\"}}}}";
        
        Payment payment = createTestPayment(PaymentStatus.CAPTURED);
        payment.setGatewayPaymentId("pay_126");
        
        when(webhookEventRepository.save(any(WebhookEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByGatewayPaymentId("pay_126"))
            .thenReturn(Optional.of(payment));

        // Act & Assert
        assertThatThrownBy(() -> webhookService.processWebhook(unsupportedPayload, SIGNATURE))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "UNSUPPORTED_EVENT_TYPE");

        verify(paymentRepository, never()).save(any());
    }

    private Payment createTestPayment(PaymentStatus status) {
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
            .gatewayPaymentId("pay_123")
            .order(order)
            .merchant(merchant)
            .amount(new BigDecimal("100.00"))
            .currency("INR")
            .status(status)
            .build();
    }
}
