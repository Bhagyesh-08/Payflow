package com.payflow.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.exception.BusinessException;
import com.payflow.common.observability.BusinessMetrics;
import com.payflow.event.service.RefundEventService;
import com.payflow.merchant.entity.Merchant;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.statemachine.PaymentStateMachine;
import com.payflow.refund.entity.Refund;
import com.payflow.refund.enums.RefundStatus;
import com.payflow.refund.repository.RefundRepository;
import com.payflow.webhook.entity.WebhookEvent;
import com.payflow.webhook.enums.WebhookStatus;
import com.payflow.webhook.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundWebhookServiceTest {

    @Mock
    private BusinessMetrics metrics;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentStateMachine stateMachine;

    @Mock
    private WebhookSignatureVerifier signatureVerifier;

    @Mock
    private RefundEventService refundEventService;

    @Mock
    private com.payflow.dispute.repository.DisputeRepository disputeRepository;

    @Mock
    private com.payflow.event.service.PaymentEventService paymentEventService;

    @Mock
    private com.payflow.event.service.DisputeEventService disputeEventService;

    @InjectMocks
    private WebhookService webhookService;

    private ObjectMapper objectMapper;
    private Merchant merchant;
    private Payment payment;
    private Refund refund;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webhookService = new WebhookService(
            metrics,
            webhookEventRepository,
            paymentRepository,
            refundRepository,
            disputeRepository,
            stateMachine,
            signatureVerifier,
            objectMapper,
            paymentEventService,
            refundEventService,
            disputeEventService
        );

        merchant = Merchant.builder()
            .id(1L)
            .businessName("Test Merchant")
            .build();

        payment = Payment.builder()
            .id(1L)
            .paymentId("PAY001")
            .merchant(merchant)
            .gatewayPaymentId("pay_GatewayPayment123")
            .amount(new BigDecimal("100.00"))
            .currency("INR")
            .build();

        refund = Refund.builder()
            .id(1L)
            .refundId("REF001")
            .payment(payment)
            .merchant(merchant)
            .gatewayRefundId("rfnd_Gateway123")
            .amount(new BigDecimal("50.00"))
            .currency("INR")
            .status(RefundStatus.REQUESTED)
            .build();
    }

    @Test
    void processWebhook_refundCreated_shouldTransitionToProcessing() {
        // Given
        String payload = """
            {
                "id": "evt_refund_created_123",
                "event": "payment.refund.created",
                "payload": {
                    "refund": {
                        "entity": {
                            "id": "rfnd_Gateway123",
                            "payment_id": "pay_GatewayPayment123",
                            "amount": 5000,
                            "currency": "INR",
                            "status": "pending"
                        }
                    }
                }
            }
            """;
        String signature = "valid_signature";

        WebhookEvent webhookEvent = WebhookEvent.builder()
            .eventId("evt_refund_created_123")
            .eventType("payment.refund.created")
            .gatewayName("RAZORPAY")
            .gatewayRefundId("rfnd_Gateway123")
            .gatewayPaymentId("pay_GatewayPayment123")
            .status(WebhookStatus.VERIFIED)
            .build();

        doNothing().when(signatureVerifier).verifySignature(payload, signature);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(webhookEvent);
        when(refundRepository.findByGatewayRefundId("rfnd_Gateway123")).thenReturn(Optional.of(refund));

        // When
        webhookService.processWebhook(payload, signature);

        // Then
        verify(refundRepository).save(argThat(r -> 
            r.getStatus() == RefundStatus.PROCESSING
        ));
        verify(webhookEventRepository, atLeastOnce()).save(argThat(we -> 
            we.getStatus() == WebhookStatus.PROCESSED
        ));
    }

    @Test
    void processWebhook_refundProcessed_succeeded_shouldTransitionToSucceeded() {
        // Given
        refund.setStatus(RefundStatus.PROCESSING);
        
        String payload = """
            {
                "id": "evt_refund_processed_123",
                "event": "payment.refund.processed",
                "payload": {
                    "refund": {
                        "entity": {
                            "id": "rfnd_Gateway123",
                            "payment_id": "pay_GatewayPayment123",
                            "amount": 5000,
                            "currency": "INR",
                            "status": "processed"
                        }
                    }
                }
            }
            """;
        String signature = "valid_signature";

        WebhookEvent webhookEvent = WebhookEvent.builder()
            .eventId("evt_refund_processed_123")
            .eventType("payment.refund.processed")
            .gatewayName("RAZORPAY")
            .gatewayRefundId("rfnd_Gateway123")
            .gatewayPaymentId("pay_GatewayPayment123")
            .status(WebhookStatus.VERIFIED)
            .build();

        doNothing().when(signatureVerifier).verifySignature(payload, signature);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(webhookEvent);
        when(refundRepository.findByGatewayRefundId("rfnd_Gateway123")).thenReturn(Optional.of(refund));

        // When
        webhookService.processWebhook(payload, signature);

        // Then
        verify(refundRepository).save(argThat(r -> 
            r.getStatus() == RefundStatus.SUCCEEDED
        ));
        verify(refundEventService).createRefundEvent(any(Refund.class), any());
        verify(webhookEventRepository, atLeastOnce()).save(argThat(we -> 
            we.getStatus() == WebhookStatus.PROCESSED
        ));
    }

    @Test
    void processWebhook_refundProcessed_failed_shouldTransitionToFailed() {
        // Given
        refund.setStatus(RefundStatus.PROCESSING);
        
        String payload = """
            {
                "id": "evt_refund_failed_123",
                "event": "payment.refund.processed",
                "payload": {
                    "refund": {
                        "entity": {
                            "id": "rfnd_Gateway123",
                            "payment_id": "pay_GatewayPayment123",
                            "amount": 5000,
                            "currency": "INR",
                            "status": "failed",
                            "error_description": "Insufficient balance"
                        }
                    }
                }
            }
            """;
        String signature = "valid_signature";

        WebhookEvent webhookEvent = WebhookEvent.builder()
            .eventId("evt_refund_failed_123")
            .eventType("payment.refund.processed")
            .gatewayName("RAZORPAY")
            .gatewayRefundId("rfnd_Gateway123")
            .gatewayPaymentId("pay_GatewayPayment123")
            .status(WebhookStatus.VERIFIED)
            .build();

        doNothing().when(signatureVerifier).verifySignature(payload, signature);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(webhookEvent);
        when(refundRepository.findByGatewayRefundId("rfnd_Gateway123")).thenReturn(Optional.of(refund));

        // When
        webhookService.processWebhook(payload, signature);

        // Then
        verify(refundRepository).save(argThat(r -> 
            r.getStatus() == RefundStatus.FAILED &&
            "Insufficient balance".equals(r.getFailureReason())
        ));
        verify(refundEventService).createRefundEvent(any(Refund.class), any());
        verify(webhookEventRepository, atLeastOnce()).save(argThat(we -> 
            we.getStatus() == WebhookStatus.PROCESSED
        ));
    }

    @Test
    void processWebhook_refundNotFound_shouldFailGracefully() {
        // Given
        String payload = """
            {
                "id": "evt_refund_unknown_123",
                "event": "payment.refund.created",
                "payload": {
                    "refund": {
                        "entity": {
                            "id": "rfnd_Unknown999",
                            "payment_id": "pay_GatewayPayment123",
                            "amount": 5000,
                            "currency": "INR",
                            "status": "pending"
                        }
                    }
                }
            }
            """;
        String signature = "valid_signature";

        WebhookEvent webhookEvent = WebhookEvent.builder()
            .eventId("evt_refund_unknown_123")
            .eventType("payment.refund.created")
            .gatewayName("RAZORPAY")
            .status(WebhookStatus.VERIFIED)
            .build();

        doNothing().when(signatureVerifier).verifySignature(payload, signature);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(webhookEvent);
        when(refundRepository.findByGatewayRefundId("rfnd_Unknown999")).thenReturn(Optional.empty());

        // When/Then
        BusinessException exception = assertThrows(BusinessException.class, () -> 
            webhookService.processWebhook(payload, signature)
        );

        assertEquals("REFUND_NOT_FOUND", exception.getErrorCode());
        verify(refundRepository, never()).save(any());
        verify(refundEventService, never()).createRefundEvent(any(), any());
    }

    @Test
    void processWebhook_refundAlreadySucceeded_shouldBeIdempotent() {
        // Given
        refund.setStatus(RefundStatus.SUCCEEDED);
        
        String payload = """
            {
                "id": "evt_refund_processed_duplicate",
                "event": "payment.refund.processed",
                "payload": {
                    "refund": {
                        "entity": {
                            "id": "rfnd_Gateway123",
                            "payment_id": "pay_GatewayPayment123",
                            "amount": 5000,
                            "currency": "INR",
                            "status": "processed"
                        }
                    }
                }
            }
            """;
        String signature = "valid_signature";

        WebhookEvent webhookEvent = WebhookEvent.builder()
            .eventId("evt_refund_processed_duplicate")
            .eventType("payment.refund.processed")
            .gatewayName("RAZORPAY")
            .gatewayRefundId("rfnd_Gateway123")
            .status(WebhookStatus.VERIFIED)
            .build();

        doNothing().when(signatureVerifier).verifySignature(payload, signature);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(webhookEvent);
        when(refundRepository.findByGatewayRefundId("rfnd_Gateway123")).thenReturn(Optional.of(refund));

        // When
        webhookService.processWebhook(payload, signature);

        // Then - no state change, no events published
        verify(refundRepository, never()).save(any());
        verify(refundEventService, never()).createRefundEvent(any(), any());
        verify(webhookEventRepository, atLeastOnce()).save(argThat(we -> 
            we.getStatus() == WebhookStatus.PROCESSED
        ));
    }

    @Test
    void processWebhook_cannotRegressFromSucceededToFailed_shouldIgnore() {
        // Given
        refund.setStatus(RefundStatus.SUCCEEDED);
        
        String payload = """
            {
                "id": "evt_refund_regression",
                "event": "payment.refund.processed",
                "payload": {
                    "refund": {
                        "entity": {
                            "id": "rfnd_Gateway123",
                            "payment_id": "pay_GatewayPayment123",
                            "amount": 5000,
                            "currency": "INR",
                            "status": "failed",
                            "error_description": "Late failure"
                        }
                    }
                }
            }
            """;
        String signature = "valid_signature";

        WebhookEvent webhookEvent = WebhookEvent.builder()
            .eventId("evt_refund_regression")
            .eventType("payment.refund.processed")
            .gatewayName("RAZORPAY")
            .gatewayRefundId("rfnd_Gateway123")
            .status(WebhookStatus.VERIFIED)
            .build();

        doNothing().when(signatureVerifier).verifySignature(payload, signature);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(webhookEvent);
        when(refundRepository.findByGatewayRefundId("rfnd_Gateway123")).thenReturn(Optional.of(refund));

        // When
        webhookService.processWebhook(payload, signature);

        // Then - state unchanged
        verify(refundRepository, never()).save(any());
        verify(refundEventService, never()).createRefundEvent(any(), any());
        assertEquals(RefundStatus.SUCCEEDED, refund.getStatus());
    }

    @Test
    void processWebhook_cannotRegressFromFailedToSucceeded_shouldIgnore() {
        // Given
        refund.setStatus(RefundStatus.FAILED);
        
        String payload = """
            {
                "id": "evt_refund_late_success",
                "event": "payment.refund.processed",
                "payload": {
                    "refund": {
                        "entity": {
                            "id": "rfnd_Gateway123",
                            "payment_id": "pay_GatewayPayment123",
                            "amount": 5000,
                            "currency": "INR",
                            "status": "processed"
                        }
                    }
                }
            }
            """;
        String signature = "valid_signature";

        WebhookEvent webhookEvent = WebhookEvent.builder()
            .eventId("evt_refund_late_success")
            .eventType("payment.refund.processed")
            .gatewayName("RAZORPAY")
            .gatewayRefundId("rfnd_Gateway123")
            .status(WebhookStatus.VERIFIED)
            .build();

        doNothing().when(signatureVerifier).verifySignature(payload, signature);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(webhookEvent);
        when(refundRepository.findByGatewayRefundId("rfnd_Gateway123")).thenReturn(Optional.of(refund));

        // When
        webhookService.processWebhook(payload, signature);

        // Then - state unchanged
        verify(refundRepository, never()).save(any());
        verify(refundEventService, never()).createRefundEvent(any(), any());
        assertEquals(RefundStatus.FAILED, refund.getStatus());
    }

    @Test
    void processWebhook_unsupportedRefundEventType_shouldIgnore() {
        // Given
        String payload = """
            {
                "id": "evt_refund_unsupported",
                "event": "payment.refund.speed_changed",
                "payload": {
                    "refund": {
                        "entity": {
                            "id": "rfnd_Gateway123",
                            "payment_id": "pay_GatewayPayment123",
                            "amount": 5000,
                            "currency": "INR"
                        }
                    }
                }
            }
            """;
        String signature = "valid_signature";

        WebhookEvent webhookEvent = WebhookEvent.builder()
            .eventId("evt_refund_unsupported")
            .eventType("payment.refund.speed_changed")
            .gatewayName("RAZORPAY")
            .status(WebhookStatus.VERIFIED)
            .build();

        doNothing().when(signatureVerifier).verifySignature(payload, signature);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(webhookEvent);
        when(refundRepository.findByGatewayRefundId("rfnd_Gateway123")).thenReturn(Optional.of(refund));

        // When/Then
        BusinessException exception = assertThrows(BusinessException.class, () -> 
            webhookService.processWebhook(payload, signature)
        );

        assertEquals("UNSUPPORTED_EVENT_TYPE", exception.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void processWebhook_invalidRefundStatus_shouldFail() {
        // Given
        refund.setStatus(RefundStatus.PROCESSING);
        
        String payload = """
            {
                "id": "evt_refund_invalid_status",
                "event": "payment.refund.processed",
                "payload": {
                    "refund": {
                        "entity": {
                            "id": "rfnd_Gateway123",
                            "payment_id": "pay_GatewayPayment123",
                            "amount": 5000,
                            "currency": "INR",
                            "status": "unknown_status"
                        }
                    }
                }
            }
            """;
        String signature = "valid_signature";

        WebhookEvent webhookEvent = WebhookEvent.builder()
            .eventId("evt_refund_invalid_status")
            .eventType("payment.refund.processed")
            .gatewayName("RAZORPAY")
            .gatewayRefundId("rfnd_Gateway123")
            .status(WebhookStatus.VERIFIED)
            .build();

        doNothing().when(signatureVerifier).verifySignature(payload, signature);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(webhookEvent);
        when(refundRepository.findByGatewayRefundId("rfnd_Gateway123")).thenReturn(Optional.of(refund));

        // When/Then
        BusinessException exception = assertThrows(BusinessException.class, () -> 
            webhookService.processWebhook(payload, signature)
        );

        assertEquals("WEBHOOK_INVALID_STATUS", exception.getErrorCode());
    }
}
