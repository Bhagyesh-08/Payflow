package com.payflow.dispute.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.security.SecurityUtils;
import com.payflow.common.util.IdGenerator;
import com.payflow.dispute.entity.Dispute;
import com.payflow.dispute.enums.DisputeStatus;
import com.payflow.dispute.repository.DisputeRepository;
import com.payflow.dispute.statemachine.DisputeStateMachine;
import com.payflow.event.domain.DisputeEventType;
import com.payflow.event.service.DisputeEventService;
import com.payflow.infrastructure.gateway.DisputeGateway;
import com.payflow.infrastructure.gateway.GatewayDisputeRequest;
import com.payflow.infrastructure.gateway.GatewayDisputeResponse;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.enums.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dispute Service.
 * 
 * Handles dispute lifecycle with gateway abstraction, state machine validation,
 * and transactional event publishing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final DisputeStateMachine stateMachine;
    private final DisputeEventService disputeEventService;
    private final DisputeGateway disputeGateway;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Dispute createDispute(
            Long paymentId,
            Long merchantId,
            BigDecimal amount,
            String currency,
            String reason,
            String gatewayDisputeId) {

        log.info("Creating dispute: payment={}, merchant={}, amount={}", paymentId, merchantId, amount);

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Dispute amount must be positive");
        }

        // Load payment
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        // Verify merchant ownership
        if (!payment.getMerchant().getId().equals(merchantId)) {
            throw new BusinessException("UNAUTHORIZED", "Payment belongs to different merchant");
        }

        // Only dispute captured payments
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new BusinessException("INVALID_PAYMENT_STATUS", 
                "Can only dispute CAPTURED payments. Current status: " + payment.getStatus());
        }

        // Verify currency
        if (!payment.getCurrency().equals(currency)) {
            throw new BusinessException("CURRENCY_MISMATCH", 
                "Dispute currency must match payment currency");
        }

        // Check if dispute already exists for this gateway dispute ID
        if (gatewayDisputeId != null) {
            var existing = disputeRepository.findByGatewayDisputeId(gatewayDisputeId);
            if (existing.isPresent()) {
                log.info("Returning existing dispute for gateway ID: {}", gatewayDisputeId);
                return existing.get();
            }
        }

        // Create dispute
        Dispute dispute = Dispute.builder()
            .disputeId(IdGenerator.generateDisputeId())
            .payment(payment)
            .merchant(payment.getMerchant())
            .amount(amount)
            .currency(currency)
            .status(DisputeStatus.OPEN)
            .gateway("RAZORPAY")
            .gatewayDisputeId(gatewayDisputeId)
            .reason(reason)
            .build();

        Dispute saved = disputeRepository.save(dispute);
        log.info("Dispute created: {}", saved.getDisputeId());

        // Create event (atomic with dispute creation)
        disputeEventService.createDisputeEvent(saved, DisputeEventType.DISPUTE_CREATED);

        return saved;
    }

    public Dispute submitEvidence(String disputeId, String evidenceDescription) {
        // Load dispute (no transaction)
        Dispute dispute = getDisputeByDisputeId(disputeId);
        
        // Verify merchant access
        SecurityUtils.validateMerchantAccess(dispute.getMerchant().getId());

        // Validate current state
        if (dispute.getStatus() != DisputeStatus.OPEN && 
            dispute.getStatus() != DisputeStatus.EVIDENCE_REQUIRED) {
            throw new BusinessException("INVALID_DISPUTE_STATE", 
                "Cannot submit evidence in state: " + dispute.getStatus());
        }

        // Call gateway (NO transaction held)
        GatewayDisputeRequest request = GatewayDisputeRequest.builder()
            .disputeId(dispute.getGatewayDisputeId())
            .evidenceDescription(evidenceDescription)
            .build();

        GatewayDisputeResponse response = disputeGateway.submitEvidence(request);

        if (!response.isSuccess()) {
            throw new BusinessException("GATEWAY_ERROR", 
                "Failed to submit evidence: " + response.getMessage());
        }

        // Persist result (atomic transaction)
        return updateDisputeAfterEvidenceSubmitted(disputeId);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    protected Dispute updateDisputeAfterEvidenceSubmitted(String disputeId) {
        Dispute dispute = getDisputeByDisputeId(disputeId);
        
        // Revalidate state (idempotency)
        if (dispute.getStatus() == DisputeStatus.EVIDENCE_SUBMITTED) {
            log.info("Dispute already in EVIDENCE_SUBMITTED state: {}", disputeId);
            return dispute;
        }

        // Validate transition
        stateMachine.validateTransition(dispute.getStatus(), DisputeStatus.EVIDENCE_SUBMITTED);

        // Update state
        dispute.setStatus(DisputeStatus.EVIDENCE_SUBMITTED);
        Dispute updated = disputeRepository.save(dispute);

        // Create event
        disputeEventService.createDisputeEvent(updated, DisputeEventType.DISPUTE_EVIDENCE_SUBMITTED);

        log.info("Dispute updated to EVIDENCE_SUBMITTED: {}", disputeId);
        return updated;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Dispute updateDisputeStatus(
            String disputeId,
            DisputeStatus newStatus,
            String resolutionReason) {

        Dispute dispute = getDisputeByDisputeId(disputeId);
        
        // Idempotency: if already in target state, return
        if (dispute.getStatus() == newStatus) {
            log.info("Dispute already in state {}: {}", newStatus, disputeId);
            return dispute;
        }

        // Validate transition
        stateMachine.validateTransition(dispute.getStatus(), newStatus);

        // Update state
        dispute.setStatus(newStatus);
        if (resolutionReason != null) {
            dispute.setResolutionReason(resolutionReason);
        }
        if (stateMachine.isTerminalState(newStatus)) {
            dispute.setResolvedAt(LocalDateTime.now());
        }

        Dispute updated = disputeRepository.save(dispute);

        // Create appropriate event
        DisputeEventType eventType = mapStatusToEventType(newStatus);
        disputeEventService.createDisputeEvent(updated, eventType);

        log.info("Dispute updated to {}: {}", newStatus, disputeId);
        return updated;
    }

    private DisputeEventType mapStatusToEventType(DisputeStatus status) {
        return switch (status) {
            case EVIDENCE_REQUIRED -> DisputeEventType.DISPUTE_EVIDENCE_REQUIRED;
            case EVIDENCE_SUBMITTED -> DisputeEventType.DISPUTE_EVIDENCE_SUBMITTED;
            case WON -> DisputeEventType.DISPUTE_WON;
            case LOST -> DisputeEventType.DISPUTE_LOST;
            case CLOSED -> DisputeEventType.DISPUTE_CLOSED;
            default -> throw new BusinessException("UNKNOWN_STATUS", "Unknown dispute status: " + status);
        };
    }

    @Transactional(readOnly = true)
    public Dispute getDisputeByDisputeId(String disputeId) {
        return disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + disputeId));
    }

    @Transactional(readOnly = true)
    public List<Dispute> getDisputesByPaymentId(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        
        SecurityUtils.validateMerchantAccess(payment.getMerchant().getId());
        return disputeRepository.findByPaymentId(paymentId);
    }

    @Transactional(readOnly = true)
    public List<Dispute> getDisputesByMerchantId(Long merchantId) {
        SecurityUtils.validateMerchantAccess(merchantId);
        return disputeRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }
}
