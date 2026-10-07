package com.payflow.payment.statemachine;

import com.payflow.common.exception.BusinessException;
import com.payflow.payment.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Payment State Machine
 * 
 * Enforces valid state transitions for payments.
 * 
 * Valid transitions:
 * CREATED -> PAYMENT_INITIATED, FAILED
 * PAYMENT_INITIATED -> AUTHORIZED, FAILED
 * AUTHORIZED -> CAPTURED, FAILED
 * CAPTURED -> (terminal)
 * FAILED -> (terminal)
 */
@Component
public class PaymentStateMachine {

    private final Map<PaymentStatus, Set<PaymentStatus>> allowedTransitions;

    public PaymentStateMachine() {
        allowedTransitions = new EnumMap<>(PaymentStatus.class);
        
        // CREATED can transition to PAYMENT_INITIATED or FAILED
        allowedTransitions.put(PaymentStatus.CREATED, 
            EnumSet.of(PaymentStatus.PAYMENT_INITIATED, PaymentStatus.FAILED));
        
        // PAYMENT_INITIATED can transition to AUTHORIZED or FAILED
        allowedTransitions.put(PaymentStatus.PAYMENT_INITIATED, 
            EnumSet.of(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED));
        
        // AUTHORIZED can transition to CAPTURED or FAILED
        allowedTransitions.put(PaymentStatus.AUTHORIZED, 
            EnumSet.of(PaymentStatus.CAPTURED, PaymentStatus.FAILED));
        
        // CAPTURED is terminal (no transitions)
        allowedTransitions.put(PaymentStatus.CAPTURED, EnumSet.noneOf(PaymentStatus.class));
        
        // FAILED is terminal (no transitions)
        allowedTransitions.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));
        
        // Future states (not yet used in Phase 2)
        allowedTransitions.put(PaymentStatus.SETTLED, EnumSet.noneOf(PaymentStatus.class));
        allowedTransitions.put(PaymentStatus.REFUND_REQUESTED, EnumSet.of(PaymentStatus.REFUNDED));
        allowedTransitions.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    /**
     * Validates if transition from current status to new status is allowed.
     *
     * @param currentStatus the current payment status
     * @param newStatus the desired new status
     * @throws BusinessException if transition is not allowed
     */
    public void validateTransition(PaymentStatus currentStatus, PaymentStatus newStatus) {
        if (currentStatus == null) {
            throw new BusinessException("INVALID_PAYMENT_STATE", 
                "Current payment status cannot be null");
        }

        if (newStatus == null) {
            throw new BusinessException("INVALID_PAYMENT_STATE", 
                "New payment status cannot be null");
        }

        // Same status is allowed (idempotent operations)
        if (currentStatus == newStatus) {
            return;
        }

        Set<PaymentStatus> validNextStatuses = allowedTransitions.get(currentStatus);
        
        if (validNextStatuses == null || !validNextStatuses.contains(newStatus)) {
            throw new BusinessException("INVALID_PAYMENT_STATE", 
                String.format("Cannot transition payment from %s to %s", currentStatus, newStatus));
        }
    }

    /**
     * Checks if a status is terminal (no further transitions possible).
     *
     * @param status the status to check
     * @return true if terminal, false otherwise
     */
    public boolean isTerminalState(PaymentStatus status) {
        Set<PaymentStatus> validTransitions = allowedTransitions.get(status);
        return validTransitions != null && validTransitions.isEmpty();
    }

    /**
     * Gets all valid next statuses from the current status.
     *
     * @param currentStatus the current status
     * @return set of valid next statuses
     */
    public Set<PaymentStatus> getValidNextStatuses(PaymentStatus currentStatus) {
        return allowedTransitions.getOrDefault(currentStatus, EnumSet.noneOf(PaymentStatus.class));
    }
}
