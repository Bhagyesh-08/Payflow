package com.payflow.dispute.statemachine;

import com.payflow.common.exception.BusinessException;
import com.payflow.dispute.enums.DisputeStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Dispute State Machine.
 * 
 * Enforces valid state transitions for disputes.
 * 
 * Valid transitions:
 * OPEN → EVIDENCE_REQUIRED, WON, LOST, CLOSED
 * EVIDENCE_REQUIRED → EVIDENCE_SUBMITTED, LOST, CLOSED
 * EVIDENCE_SUBMITTED → WON, LOST, CLOSED
 * WON → (terminal)
 * LOST → (terminal)
 * CLOSED → (terminal)
 */
@Component
public class DisputeStateMachine {

    private final Map<DisputeStatus, Set<DisputeStatus>> allowedTransitions;

    public DisputeStateMachine() {
        allowedTransitions = new EnumMap<>(DisputeStatus.class);
        
        // OPEN can transition to EVIDENCE_REQUIRED, WON, LOST, or CLOSED
        allowedTransitions.put(DisputeStatus.OPEN, 
            EnumSet.of(DisputeStatus.EVIDENCE_REQUIRED, DisputeStatus.WON, 
                      DisputeStatus.LOST, DisputeStatus.CLOSED));
        
        // EVIDENCE_REQUIRED can transition to EVIDENCE_SUBMITTED, LOST, or CLOSED
        allowedTransitions.put(DisputeStatus.EVIDENCE_REQUIRED, 
            EnumSet.of(DisputeStatus.EVIDENCE_SUBMITTED, DisputeStatus.LOST, DisputeStatus.CLOSED));
        
        // EVIDENCE_SUBMITTED can transition to WON, LOST, or CLOSED
        allowedTransitions.put(DisputeStatus.EVIDENCE_SUBMITTED, 
            EnumSet.of(DisputeStatus.WON, DisputeStatus.LOST, DisputeStatus.CLOSED));
        
        // WON is terminal
        allowedTransitions.put(DisputeStatus.WON, EnumSet.noneOf(DisputeStatus.class));
        
        // LOST is terminal
        allowedTransitions.put(DisputeStatus.LOST, EnumSet.noneOf(DisputeStatus.class));
        
        // CLOSED is terminal
        allowedTransitions.put(DisputeStatus.CLOSED, EnumSet.noneOf(DisputeStatus.class));
    }

    /**
     * Validates if transition from current status to new status is allowed.
     *
     * @param currentStatus the current dispute status
     * @param newStatus the desired new status
     * @throws BusinessException if transition is not allowed
     */
    public void validateTransition(DisputeStatus currentStatus, DisputeStatus newStatus) {
        if (currentStatus == null) {
            throw new BusinessException("INVALID_DISPUTE_STATE", 
                "Current dispute status cannot be null");
        }

        if (newStatus == null) {
            throw new BusinessException("INVALID_DISPUTE_STATE", 
                "New dispute status cannot be null");
        }

        // Same status is allowed (idempotent operations)
        if (currentStatus == newStatus) {
            return;
        }

        Set<DisputeStatus> validNextStatuses = allowedTransitions.get(currentStatus);
        
        if (validNextStatuses == null || !validNextStatuses.contains(newStatus)) {
            throw new BusinessException("INVALID_DISPUTE_STATE", 
                String.format("Cannot transition dispute from %s to %s", currentStatus, newStatus));
        }
    }

    /**
     * Checks if a status is terminal (no further transitions possible).
     *
     * @param status the status to check
     * @return true if terminal, false otherwise
     */
    public boolean isTerminalState(DisputeStatus status) {
        Set<DisputeStatus> validTransitions = allowedTransitions.get(status);
        return validTransitions != null && validTransitions.isEmpty();
    }

    /**
     * Gets all valid next statuses from the current status.
     *
     * @param currentStatus the current status
     * @return set of valid next statuses
     */
    public Set<DisputeStatus> getValidNextStatuses(DisputeStatus currentStatus) {
        return allowedTransitions.getOrDefault(currentStatus, EnumSet.noneOf(DisputeStatus.class));
    }
}
