package com.payflow.refund.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.observability.BusinessMetrics;
import com.payflow.common.security.SecurityUtils;
import com.payflow.common.util.IdGenerator;
import com.payflow.event.domain.RefundEventType;
import com.payflow.event.service.RefundEventService;
import com.payflow.infrastructure.gateway.GatewayRefundRequest;
import com.payflow.infrastructure.gateway.GatewayRefundResponse;
import com.payflow.infrastructure.gateway.RefundGateway;
import com.payflow.ledger.service.LedgerService;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.enums.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.refund.entity.Refund;
import com.payflow.refund.enums.RefundStatus;
import com.payflow.refund.repository.RefundRepository;
import com.payflow.refund.statemachine.RefundStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;
    private final RefundEventService refundEventService;
    private final RefundGateway refundGateway;
    private final RefundStateMachine stateMachine;
    private final BusinessMetrics metrics;

    /**
     * Creates refund with business rules and idempotency.
     * 
     * Rules:
     * - Only CAPTURED payments can be refunded
     * - Refund amount must be positive
     * - Total refunds cannot exceed captured amount
     * - Same idempotency key returns existing refund
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Refund createRefund(
            Long paymentId,
            Long merchantId,
            BigDecimal amount,
            String currency,
            String reason,
            String idempotencyKey) {

        log.info("Creating refund: payment={}, merchant={}, amount={}, idempotency={}", 
            paymentId, merchantId, amount, idempotencyKey);

        // Validate merchant parameter matches authenticated merchant
        SecurityUtils.validateMerchantAccess(merchantId);

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Refund amount must be positive");
        }

        // Load payment
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "Payment not found: " + paymentId));

        // Verify merchant ownership
        SecurityUtils.validateMerchantAccess(payment.getMerchant().getId());

        // Rule E: Only refund captured payments
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new BusinessException("INVALID_PAYMENT_STATUS", 
                "Can only refund CAPTURED payments. Current status: " + payment.getStatus());
        }

        // Verify currency
        if (!payment.getCurrency().equals(currency)) {
            throw new BusinessException("CURRENCY_MISMATCH", 
                "Refund currency must match payment currency");
        }

        // Rule D: Check refundable amount
        BigDecimal alreadyRefunded = refundRepository.sumSuccessfulRefundsByPaymentId(
            paymentId, RefundStatus.SUCCEEDED);
        BigDecimal refundableAmount = payment.getAmount().subtract(alreadyRefunded);

        if (amount.compareTo(refundableAmount) > 0) {
            throw new BusinessException("OVER_REFUND", 
                String.format("Cannot refund %s. Available: %s (already refunded: %s of %s)", 
                    amount, refundableAmount, alreadyRefunded, payment.getAmount()));
        }

        // Create refund
        Refund refund = Refund.builder()
            .refundId(IdGenerator.generateRefundId())
            .payment(payment)
            .merchant(payment.getMerchant())
            .amount(amount)
            .currency(currency)
            .status(RefundStatus.REQUESTED)
            .gateway(payment.getGatewayPaymentId() != null ? "RAZORPAY" : null)
            .idempotencyKey(idempotencyKey)
            .reason(reason)
            .build();

        try {
            refund = refundRepository.save(refund);
            MDC.put("refundId", refund.getRefundId());

            // Publish REFUND_CREATED event (same transaction)
            refundEventService.createRefundEvent(refund, RefundEventType.REFUND_CREATED);

            log.info("Refund created: refund_id={}, payment_id={}, amount={}", 
                refund.getRefundId(), payment.getPaymentId(), amount);
            
            metrics.incrementRefundsRequested();

            return refund;

        } catch (DataIntegrityViolationException e) {
            // Duplicate idempotency key (race condition) - return existing
            log.info("Duplicate idempotency key detected: merchant_id={}, key={}", merchantId, idempotencyKey);
            return refundRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .orElseThrow(() -> new BusinessException("REFUND_CREATION_FAILED", 
                    "Failed to create or retrieve refund"));
        }
    }

    /**
     * Marks refund as succeeded and creates compensating ledger transaction.
     * 
     * CRITICAL: Creates NEW ledger transaction. Never modifies original.
     */
    @Transactional
    public void markRefundSucceeded(String refundId, String gatewayRefundId) {
        log.info("Marking refund succeeded: refund_id={}, gateway_ref={}", refundId, gatewayRefundId);

        Refund refund = refundRepository.findByRefundId(refundId)
            .orElseThrow(() -> new BusinessException("REFUND_NOT_FOUND", "Refund not found: " + refundId));

        // Check if already in target state (idempotent)
        if (refund.getStatus() == RefundStatus.SUCCEEDED) {
            log.info("Refund already succeeded: refund_id={}", refundId);
            return;
        }

        // Validate state machine transition
        stateMachine.validateTransition(refund.getStatus(), RefundStatus.SUCCEEDED);

        refund.setStatus(RefundStatus.SUCCEEDED);
        refund.setGatewayRefundId(gatewayRefundId);
        refundRepository.save(refund);

        // Create compensating ledger transaction
        ledgerService.recordRefund(
            refund.getRefundId(),
            refund.getPayment().getPaymentId(),
            refund.getMerchant().getId(),
            refund.getAmount(),
            refund.getCurrency()
        );

        // Publish REFUND_SUCCEEDED event (same transaction)
        refundEventService.createRefundEvent(refund, RefundEventType.REFUND_SUCCEEDED);

        log.info("Refund succeeded: refund_id={}, ledger created", refundId);
        metrics.incrementRefundsSucceeded();
    }

    @Transactional
    public void markRefundFailed(String refundId, String failureReason) {
        log.info("Marking refund failed: refund_id={}, reason={}", refundId, failureReason);

        Refund refund = refundRepository.findByRefundId(refundId)
            .orElseThrow(() -> new BusinessException("REFUND_NOT_FOUND", "Refund not found: " + refundId));

        // Check if already failed (idempotent)
        if (refund.getStatus() == RefundStatus.FAILED) {
            log.info("Refund already failed: refund_id={}", refundId);
            return;
        }

        // Validate state machine transition
        stateMachine.validateTransition(refund.getStatus(), RefundStatus.FAILED);

        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason(failureReason);
        refundRepository.save(refund);

        // Publish REFUND_FAILED event (same transaction)
        refundEventService.createRefundEvent(refund, RefundEventType.REFUND_FAILED);

        log.info("Refund failed: refund_id={}", refundId);
        metrics.incrementRefundsFailed();
    }

    @Transactional(readOnly = true)
    public Refund getRefundById(String refundId) {
        return refundRepository.findByRefundId(refundId)
            .orElseThrow(() -> new BusinessException("REFUND_NOT_FOUND", "Refund not found: " + refundId));
    }

    /**
     * Executes refund through gateway.
     * 
     * Transaction boundary:
     * 1. Persist REQUESTED state (already done by createRefund)
     * 2. Call gateway (outside transaction)
     * 3. Persist PROCESSING/FAILED + gateway ID
     * 
     * Gateway acceptance means request accepted, NOT money refunded.
     * Final success established through webhook/polling.
     */
    public void executeRefund(String refundId) {
        log.info("Executing refund through gateway: refund_id={}", refundId);

        // Load refund (read-only, no transaction hold)
        Refund refund = getRefundById(refundId);

        // Validate state
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            log.warn("Refund not in REQUESTED state: refund_id={}, status={}", 
                refundId, refund.getStatus());
            return;
        }

        // Validate payment has gateway reference
        if (refund.getPayment().getGatewayPaymentId() == null) {
            log.error("Payment has no gateway reference: payment_id={}", refund.getPayment().getPaymentId());
            markRefundFailed(refundId, "Payment has no gateway reference");
            return;
        }

        // Build gateway request
        GatewayRefundRequest gatewayRequest = GatewayRefundRequest.builder()
            .internalRefundId(refund.getRefundId())
            .gatewayPaymentId(refund.getPayment().getGatewayPaymentId())
            .amount(refund.getAmount())
            .currency(refund.getCurrency())
            .reason(refund.getReason())
            .build();

        try {
            // Call gateway (NO database transaction held)
            long startTime = System.currentTimeMillis();
            GatewayRefundResponse gatewayResponse = refundGateway.createRefund(gatewayRequest);
            long duration = System.currentTimeMillis() - startTime;
            
            metrics.recordRefundGatewayLatency(duration);

            if (gatewayResponse.isSuccess()) {
                // Gateway accepted refund request
                markRefundProcessing(refundId, gatewayResponse.getGatewayRefundId());
            } else {
                // Gateway rejected refund
                String failureReason = String.format("%s: %s", 
                    gatewayResponse.getErrorCode(), 
                    gatewayResponse.getMessage());
                markRefundFailed(refundId, failureReason);
            }

        } catch (Exception e) {
            log.error("Unexpected error during gateway refund execution: refund_id={}", refundId, e);
            markRefundFailed(refundId, "Gateway execution error: " + e.getMessage());
        }
    }

    /**
     * Marks refund as PROCESSING after gateway acceptance.
     * Gateway acceptance means request submitted, not final success.
     */
    @Transactional
    public void markRefundProcessing(String refundId, String gatewayRefundId) {
        log.info("Marking refund processing: refund_id={}, gateway_ref={}", refundId, gatewayRefundId);

        Refund refund = refundRepository.findByRefundId(refundId)
            .orElseThrow(() -> new BusinessException("REFUND_NOT_FOUND", "Refund not found: " + refundId));

        // Check if already processing (idempotent)
        if (refund.getStatus() == RefundStatus.PROCESSING) {
            log.info("Refund already processing: refund_id={}", refundId);
            return;
        }

        // Validate state machine transition
        stateMachine.validateTransition(refund.getStatus(), RefundStatus.PROCESSING);

        refund.setStatus(RefundStatus.PROCESSING);
        refund.setGatewayRefundId(gatewayRefundId);
        refundRepository.save(refund);

        log.info("Refund marked as processing: refund_id={}", refundId);
    }
}
