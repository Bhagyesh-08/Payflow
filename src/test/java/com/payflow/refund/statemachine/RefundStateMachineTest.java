package com.payflow.refund.statemachine;

import com.payflow.common.exception.BusinessException;
import com.payflow.refund.enums.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundStateMachineTest {

    private RefundStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new RefundStateMachine();
    }

    // REQUESTED transitions
    @Test
    void shouldAllowRequestedToProcessing() {
        stateMachine.validateTransition(RefundStatus.REQUESTED, RefundStatus.PROCESSING);
    }

    @Test
    void shouldAllowRequestedToFailed() {
        stateMachine.validateTransition(RefundStatus.REQUESTED, RefundStatus.FAILED);
    }

    @Test
    void shouldNotAllowRequestedToSucceeded() {
        assertThatThrownBy(() -> 
            stateMachine.validateTransition(RefundStatus.REQUESTED, RefundStatus.SUCCEEDED))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Cannot transition refund from REQUESTED to SUCCEEDED");
    }

    // PROCESSING transitions
    @Test
    void shouldAllowProcessingToSucceeded() {
        stateMachine.validateTransition(RefundStatus.PROCESSING, RefundStatus.SUCCEEDED);
    }

    @Test
    void shouldAllowProcessingToFailed() {
        stateMachine.validateTransition(RefundStatus.PROCESSING, RefundStatus.FAILED);
    }

    @Test
    void shouldNotAllowProcessingToRequested() {
        assertThatThrownBy(() -> 
            stateMachine.validateTransition(RefundStatus.PROCESSING, RefundStatus.REQUESTED))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Cannot transition refund from PROCESSING to REQUESTED");
    }

    // SUCCEEDED terminal state
    @Test
    void shouldNotAllowSucceededToProcessing() {
        assertThatThrownBy(() -> 
            stateMachine.validateTransition(RefundStatus.SUCCEEDED, RefundStatus.PROCESSING))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Cannot transition refund from SUCCEEDED to PROCESSING");
    }

    @Test
    void shouldNotAllowSucceededToFailed() {
        assertThatThrownBy(() -> 
            stateMachine.validateTransition(RefundStatus.SUCCEEDED, RefundStatus.FAILED))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Cannot transition refund from SUCCEEDED to FAILED");
    }

    // FAILED terminal state
    @Test
    void shouldNotAllowFailedToSucceeded() {
        assertThatThrownBy(() -> 
            stateMachine.validateTransition(RefundStatus.FAILED, RefundStatus.SUCCEEDED))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Cannot transition refund from FAILED to SUCCEEDED");
    }

    @Test
    void shouldNotAllowFailedToProcessing() {
        assertThatThrownBy(() -> 
            stateMachine.validateTransition(RefundStatus.FAILED, RefundStatus.PROCESSING))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Cannot transition refund from FAILED to PROCESSING");
    }

    // Idempotency (same status)
    @Test
    void shouldAllowSameStateTransition() {
        stateMachine.validateTransition(RefundStatus.REQUESTED, RefundStatus.REQUESTED);
        stateMachine.validateTransition(RefundStatus.PROCESSING, RefundStatus.PROCESSING);
        stateMachine.validateTransition(RefundStatus.SUCCEEDED, RefundStatus.SUCCEEDED);
        stateMachine.validateTransition(RefundStatus.FAILED, RefundStatus.FAILED);
    }

    // Terminal states
    @Test
    void shouldIdentifyTerminalStates() {
        assertThat(stateMachine.isTerminalState(RefundStatus.SUCCEEDED)).isTrue();
        assertThat(stateMachine.isTerminalState(RefundStatus.FAILED)).isTrue();
        assertThat(stateMachine.isTerminalState(RefundStatus.REQUESTED)).isFalse();
        assertThat(stateMachine.isTerminalState(RefundStatus.PROCESSING)).isFalse();
    }

    // Valid next statuses
    @Test
    void shouldReturnValidNextStatusesForRequested() {
        Set<RefundStatus> validNext = stateMachine.getValidNextStatuses(RefundStatus.REQUESTED);
        assertThat(validNext).containsExactlyInAnyOrder(RefundStatus.PROCESSING, RefundStatus.FAILED);
    }

    @Test
    void shouldReturnValidNextStatusesForProcessing() {
        Set<RefundStatus> validNext = stateMachine.getValidNextStatuses(RefundStatus.PROCESSING);
        assertThat(validNext).containsExactlyInAnyOrder(RefundStatus.SUCCEEDED, RefundStatus.FAILED);
    }

    @Test
    void shouldReturnEmptyForTerminalStates() {
        assertThat(stateMachine.getValidNextStatuses(RefundStatus.SUCCEEDED)).isEmpty();
        assertThat(stateMachine.getValidNextStatuses(RefundStatus.FAILED)).isEmpty();
    }

    // Null handling
    @Test
    void shouldThrowOnNullCurrentStatus() {
        assertThatThrownBy(() -> 
            stateMachine.validateTransition(null, RefundStatus.PROCESSING))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Current refund status cannot be null");
    }

    @Test
    void shouldThrowOnNullNewStatus() {
        assertThatThrownBy(() -> 
            stateMachine.validateTransition(RefundStatus.REQUESTED, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("New refund status cannot be null");
    }
}
