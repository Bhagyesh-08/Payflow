package com.payflow.refund.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.event.service.RefundEventService;
import com.payflow.infrastructure.gateway.GatewayRefundResponse;
import com.payflow.infrastructure.gateway.RefundGateway;
import com.payflow.ledger.service.LedgerService;
import com.payflow.merchant.entity.Merchant;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.enums.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.refund.entity.Refund;
import com.payflow.refund.enums.RefundStatus;
import com.payflow.refund.repository.RefundRepository;
import com.payflow.refund.statemachine.RefundStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private RefundEventService refundEventService;

    @Mock
    private RefundGateway refundGateway;

    @Mock
    private RefundStateMachine stateMachine;

    @InjectMocks
    private RefundService refundService;

    private Merchant merchant;
    private Payment payment;

    @BeforeEach
    void setUp() {
        merchant = Merchant.builder()
            .id(1L)
            .businessName("Test Merchant")
            .build();

        payment = Payment.builder()
            .id(1L)
            .paymentId("PAY_123")
            .merchant(merchant)
            .amount(new BigDecimal("1000.00"))
            .currency("INR")
            .status(PaymentStatus.CAPTURED)
            .build();
    }

    @Test
    void createRefund_FullRefund_Success() {
        // Arrange
        when(refundRepository.findByMerchantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(refundRepository.sumSuccessfulRefundsByPaymentId(1L, RefundStatus.SUCCEEDED))
            .thenReturn(BigDecimal.ZERO);
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Refund refund = refundService.createRefund(
            1L, 1L, new BigDecimal("1000.00"), "INR", "Customer request", "key-1");

        // Assert
        assertThat(refund).isNotNull();
        assertThat(refund.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        verify(refundRepository).save(any(Refund.class));
    }

    @Test
    void createRefund_PartialRefund_Success() {
        // Arrange
        when(refundRepository.findByMerchantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(refundRepository.sumSuccessfulRefundsByPaymentId(1L, RefundStatus.SUCCEEDED))
            .thenReturn(BigDecimal.ZERO);
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Refund refund = refundService.createRefund(
            1L, 1L, new BigDecimal("300.00"), "INR", "Partial refund", "key-2");

        // Assert
        assertThat(refund.getAmount()).isEqualByComparingTo("300.00");
        verify(refundRepository).save(any(Refund.class));
    }

    @Test
    void createRefund_OverRefund_Rejected() {
        // Arrange
        when(refundRepository.findByMerchantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(refundRepository.sumSuccessfulRefundsByPaymentId(1L, RefundStatus.SUCCEEDED))
            .thenReturn(new BigDecimal("800.00")); // Already refunded 800

        // Act & Assert
        assertThatThrownBy(() -> refundService.createRefund(
            1L, 1L, new BigDecimal("300.00"), "INR", "Over-refund", "key-3"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("OVER_REFUND");
    }

    @Test
    void createRefund_UncapturedPayment_Rejected() {
        // Arrange
        payment.setStatus(PaymentStatus.AUTHORIZED);
        when(refundRepository.findByMerchantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThatThrownBy(() -> refundService.createRefund(
            1L, 1L, new BigDecimal("100.00"), "INR", "Refund", "key-4"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("INVALID_PAYMENT_STATUS");
    }

    @Test
    void createRefund_ZeroAmount_Rejected() {
        // Arrange
        when(refundRepository.findByMerchantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> refundService.createRefund(
            1L, 1L, BigDecimal.ZERO, "INR", "Zero refund", "key-5"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void createRefund_NegativeAmount_Rejected() {
        // Arrange
        when(refundRepository.findByMerchantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> refundService.createRefund(
            1L, 1L, new BigDecimal("-100.00"), "INR", "Negative refund", "key-6"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void createRefund_CurrencyMismatch_Rejected() {
        // Arrange
        when(refundRepository.findByMerchantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThatThrownBy(() -> refundService.createRefund(
            1L, 1L, new BigDecimal("100.00"), "USD", "Wrong currency", "key-7"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void createRefund_Idempotent_ReturnsExisting() {
        // Arrange - simulate duplicate idempotency key scenario
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(refundRepository.sumSuccessfulRefundsByPaymentId(1L, RefundStatus.SUCCEEDED))
            .thenReturn(BigDecimal.ZERO);
        
        // First call succeeds and saves
        when(refundRepository.save(any(Refund.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key"));
        
        // Second call returns existing
        Refund existingRefund = Refund.builder()
            .id(1L)
            .refundId("REF_123")
            .build();
        when(refundRepository.findByMerchantIdAndIdempotencyKey(1L, "key-8"))
            .thenReturn(Optional.of(existingRefund));

        // Act
        Refund refund = refundService.createRefund(
            1L, 1L, new BigDecimal("100.00"), "INR", "Duplicate", "key-8");

        // Assert
        assertThat(refund.getId()).isEqualTo(1L);
        verify(refundRepository).findByMerchantIdAndIdempotencyKey(1L, "key-8");
    }

    @Test
    void createRefund_WrongMerchant_Rejected() {
        // Arrange
        when(refundRepository.findByMerchantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThatThrownBy(() -> refundService.createRefund(
            1L, 999L, new BigDecimal("100.00"), "INR", "Wrong merchant", "key-9"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void markRefundSucceeded_CreatesLedgerTransaction() {
        // Arrange
        Refund refund = Refund.builder()
            .id(1L)
            .refundId("REF_123")
            .payment(payment)
            .merchant(merchant)
            .amount(new BigDecimal("100.00"))
            .currency("INR")
            .status(RefundStatus.REQUESTED)
            .build();

        when(refundRepository.findByRefundId("REF_123")).thenReturn(Optional.of(refund));
        when(refundRepository.save(any(Refund.class))).thenReturn(refund);

        // Act
        refundService.markRefundSucceeded("REF_123", "gw_ref_123");

        // Assert
        verify(ledgerService).recordRefund(
            eq("REF_123"),
            eq("PAY_123"),
            eq(1L),
            eq(new BigDecimal("100.00")),
            eq("INR")
        );
    }

    @Test
    void markRefundSucceeded_AlreadySucceeded_Idempotent() {
        // Arrange
        Refund refund = Refund.builder()
            .refundId("REF_123")
            .status(RefundStatus.SUCCEEDED)
            .build();

        when(refundRepository.findByRefundId("REF_123")).thenReturn(Optional.of(refund));

        // Act
        refundService.markRefundSucceeded("REF_123", "gw_ref");

        // Assert
        verify(ledgerService, never()).recordRefund(any(), any(), any(), any(), any());
    }

    @Test
    void executeRefund_ValidRefund_InvokesGateway() {
        // Arrange
        payment.setGatewayPaymentId("pay_gateway_123");
        Refund refund = Refund.builder()
            .id(1L)
            .refundId("REF_123")
            .payment(payment)
            .merchant(merchant)
            .amount(new BigDecimal("100.00"))
            .currency("INR")
            .status(RefundStatus.REQUESTED)
            .build();

        when(refundRepository.findByRefundId("REF_123")).thenReturn(Optional.of(refund));
        when(refundGateway.createRefund(any())).thenReturn(
            GatewayRefundResponse.builder().success(true).gatewayRefundId("gw_ref_123").build()
        );

        // Act
        refundService.executeRefund("REF_123");

        // Assert
        verify(refundGateway).createRefund(any());
    }

    @Test
    void executeRefund_WrongStatus_SkipsGateway() {
        // Arrange
        Refund refund = Refund.builder()
            .refundId("REF_123")
            .status(RefundStatus.PROCESSING)
            .build();

        when(refundRepository.findByRefundId("REF_123")).thenReturn(Optional.of(refund));

        // Act
        refundService.executeRefund("REF_123");

        // Assert
        verify(refundGateway, never()).createRefund(any());
    }
}
