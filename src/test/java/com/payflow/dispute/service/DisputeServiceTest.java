package com.payflow.dispute.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.dispute.entity.Dispute;
import com.payflow.dispute.enums.DisputeStatus;
import com.payflow.dispute.repository.DisputeRepository;
import com.payflow.dispute.statemachine.DisputeStateMachine;
import com.payflow.event.domain.DisputeEventType;
import com.payflow.event.service.DisputeEventService;
import com.payflow.infrastructure.gateway.DisputeGateway;
import com.payflow.infrastructure.gateway.GatewayDisputeResponse;
import com.payflow.merchant.entity.Merchant;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.enums.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private DisputeStateMachine stateMachine;

    @Mock
    private DisputeEventService disputeEventService;

    @Mock
    private DisputeGateway disputeGateway;

    @InjectMocks
    private DisputeService disputeService;

    private Payment payment;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = Merchant.builder()
            .id(1L)
            .merchantCode("MRCH_TEST")
            .build();

        payment = Payment.builder()
            .id(1L)
            .paymentId("PAY_123")
            .merchant(merchant)
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .status(PaymentStatus.CAPTURED)
            .build();
    }

    @Test
    void shouldCreateDispute() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(disputeRepository.findByGatewayDisputeId(anyString())).thenReturn(Optional.empty());
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(i -> i.getArgument(0));

        Dispute dispute = disputeService.createDispute(
            1L, 1L, new BigDecimal("100.00"), "USD", "Chargeback", "gw_disp_123");

        assertThat(dispute).isNotNull();
        assertThat(dispute.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(dispute.getCurrency()).isEqualTo("USD");
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.OPEN);

        verify(disputeRepository).save(any(Dispute.class));
        verify(disputeEventService).createDisputeEvent(any(Dispute.class), eq(DisputeEventType.DISPUTE_CREATED));
    }

    @Test
    void shouldRejectDisputeCreationForNonCapturedPayment() {
        payment.setStatus(PaymentStatus.CREATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> disputeService.createDispute(
            1L, 1L, new BigDecimal("100.00"), "USD", "Chargeback", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Can only dispute CAPTURED payments");
    }

    @Test
    void shouldRejectDisputeCreationForInvalidAmount() {
        assertThatThrownBy(() -> disputeService.createDispute(
            1L, 1L, new BigDecimal("-10.00"), "USD", "Chargeback", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Dispute amount must be positive");
    }

    @Test
    void shouldRejectDisputeCreationForUnauthorizedMerchant() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> disputeService.createDispute(
            1L, 999L, new BigDecimal("100.00"), "USD", "Chargeback", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Payment belongs to different merchant");
    }

    @Test
    void shouldReturnExistingDisputeForDuplicateGatewayId() {
        Dispute existingDispute = Dispute.builder()
            .disputeId("DSP_123")
            .status(DisputeStatus.OPEN)
            .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(disputeRepository.findByGatewayDisputeId("gw_disp_123"))
            .thenReturn(Optional.of(existingDispute));

        Dispute dispute = disputeService.createDispute(
            1L, 1L, new BigDecimal("100.00"), "USD", "Chargeback", "gw_disp_123");

        assertThat(dispute).isEqualTo(existingDispute);
        verify(disputeRepository, never()).save(any());
        verify(disputeEventService, never()).createDisputeEvent(any(), any());
    }

    @Test
    void shouldUpdateDisputeStatus() {
        Dispute dispute = Dispute.builder()
            .disputeId("DSP_123")
            .payment(payment)
            .merchant(merchant)
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .status(DisputeStatus.OPEN)
            .build();

        when(disputeRepository.findByDisputeId("DSP_123")).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(i -> {
            Dispute saved = i.getArgument(0);
            saved.setResolvedAt(java.time.LocalDateTime.now());
            return saved;
        });

        Dispute updated = disputeService.updateDisputeStatus("DSP_123", DisputeStatus.LOST, "Insufficient evidence");

        assertThat(updated.getStatus()).isEqualTo(DisputeStatus.LOST);
        assertThat(updated.getResolutionReason()).isEqualTo("Insufficient evidence");
        assertThat(updated.getResolvedAt()).isNotNull();

        verify(stateMachine).validateTransition(DisputeStatus.OPEN, DisputeStatus.LOST);
        verify(disputeEventService).createDisputeEvent(any(Dispute.class), eq(DisputeEventType.DISPUTE_LOST));
    }

    @Test
    void shouldBeIdempotentForSameStatus() {
        Dispute dispute = Dispute.builder()
            .disputeId("DSP_123")
            .status(DisputeStatus.LOST)
            .build();

        when(disputeRepository.findByDisputeId("DSP_123")).thenReturn(Optional.of(dispute));

        Dispute result = disputeService.updateDisputeStatus("DSP_123", DisputeStatus.LOST, null);

        assertThat(result).isEqualTo(dispute);
        verify(stateMachine, never()).validateTransition(any(), any());
        verify(disputeRepository, never()).save(any());
        verify(disputeEventService, never()).createDisputeEvent(any(), any());
    }

    @Test
    void shouldThrowNotFoundForNonExistentDispute() {
        when(disputeRepository.findByDisputeId("DSP_999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.getDisputeByDisputeId("DSP_999"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Dispute not found: DSP_999");
    }
}
