package com.payflow.refund.statemachine;

import com.payflow.common.exception.BusinessException;
import com.payflow.refund.enums.RefundStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Refund State Machine
 * 
 * Enforces valid state transitions for refunds.
 * 
 * Valid transitions:
 * REQUESTED → PROCESSING, FAILED
 * PROCESSING → SUCCEEDED, FAILED
 * SUCCEEDED → (terminal)
 * FAILED → (terminal)
 */
@Component
public class RefundStateMachine {

    private final Map<RefundStatus, Set<RefundStatus>> allowedTransitions;

    public RefundStateMachine() {
        allowedTransitions = new EnumMap<>(RefundStatus.class);
        
        // REQUESTED can transition to PROCESSING or FAILED
        allowedTransitions.put(RefundStatus.REQUESTED, 
            EnumSet.of(RefundStatus.PROCESSING, RefundStatus.FAILED));
        
        // PROCESSING can transition to SUCCEEDED or FAILED
        allowedTransitions.put(RefundStatus.PROCESSING, 
            EnumSet.of(RefundStatus.SUCCEEDED, RefundStatus.FAILED));
        
        // SUCCEEDED is terminal (no transitions)
        allowedTransitions.put(RefundStatus.SUCCEEDED, EnumSet.noneOf(RefundStatus.class));
        
        // FAILED is terminal (no transitions)
        allowedTransitions.put(RefundStatus.FAILED, EnumSet.noneOf(RefundStatus.class));
    }

    /**
     * Validates if transition from current status to new status is allowed.
     *
     * @param currentStatus the current refund status
     * @param newStatus the desired new status
     * @throws BusinessException if transition is not allowed
     */
    public void validateTransition(RefundStatus currentStatus, RefundStatus newStatus) {
        if (currentStatus == null) {
            throw new BusinessException("INVALID_REFUND_STATE", 
                "Current refund status cannot be null");
        }

        if (newStatus == null) {
            throw new BusinessException("INVALID_REFUND_STATE", 
                "New refund status cannot be null");
        }

        // Same status is allowed (idempotent operations)
        if (currentStatus == newStatus) {
            return;
        }

        Set<RefundStatus> validNextStatuses = allowedTransitions.get(currentStatus);
        
        if (validNextStatuses == null || !validNextStatuses.contains(newStatus)) {
            throw new BusinessException("INVALID_REFUND_STATE", 
                String.format("Cannot transition refund from %s to %s", currentStatus, newStatus));
        }
    }

    /**
     * Checks if a status is terminal (no further transitions possible).
     *
     * @param status the status to check
     * @return true if terminal, false otherwise
     */
    public boolean isTerminalState(RefundStatus status) {
        Set<RefundStatus> validTransitions = allowedTransitions.get(status);
        return validTransitions != null && validTransitions.isEmpty();
    }

    /**
     * Gets all valid next statuses from the current status.
     *
     * @param currentStatus the current status
     * @return set of valid next statuses
     */
    public Set<RefundStatus> getValidNextStatuses(RefundStatus currentStatus) {
        return allowedTransitions.getOrDefault(currentStatus, EnumSet.noneOf(RefundStatus.class));
    }
}
