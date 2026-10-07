package com.payflow.dispute.statemachine;

import com.payflow.common.exception.BusinessException;
import com.payflow.dispute.enums.DisputeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DisputeStateMachineTest {

    private DisputeStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new DisputeStateMachine();
    }

    @Test
    void shouldAllowOpenToEvidenceRequired() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(DisputeStatus.OPEN, DisputeStatus.EVIDENCE_REQUIRED));
    }

    @Test
    void shouldAllowOpenToWon() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(DisputeStatus.OPEN, DisputeStatus.WON));
    }

    @Test
    void shouldAllowOpenToLost() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(DisputeStatus.OPEN, DisputeStatus.LOST));
    }

    @Test
    void shouldAllowOpenToClosed() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(DisputeStatus.OPEN, DisputeStatus.CLOSED));
    }

    @Test
    void shouldAllowEvidenceRequiredToEvidenceSubmitted() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(
                DisputeStatus.EVIDENCE_REQUIRED, DisputeStatus.EVIDENCE_SUBMITTED));
    }

    @Test
    void shouldAllowEvidenceRequiredToLost() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(DisputeStatus.EVIDENCE_REQUIRED, DisputeStatus.LOST));
    }

    @Test
    void shouldAllowEvidenceRequiredToClosed() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(DisputeStatus.EVIDENCE_REQUIRED, DisputeStatus.CLOSED));
    }

    @Test
    void shouldAllowEvidenceSubmittedToWon() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(DisputeStatus.EVIDENCE_SUBMITTED, DisputeStatus.WON));
    }

    @Test
    void shouldAllowEvidenceSubmittedToLost() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(DisputeStatus.EVIDENCE_SUBMITTED, DisputeStatus.LOST));
    }

    @Test
    void shouldAllowEvidenceSubmittedToClosed() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(
                DisputeStatus.EVIDENCE_SUBMITTED, DisputeStatus.CLOSED));
    }

    @Test
    void shouldAllowSameStateTransition() {
        // Idempotent operations
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(DisputeStatus.OPEN, DisputeStatus.OPEN));
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(DisputeStatus.WON, DisputeStatus.WON));
    }

    @Test
    void shouldRejectInvalidTransitions() {
        assertThatThrownBy(() -> stateMachine.validateTransition(DisputeStatus.OPEN, DisputeStatus.EVIDENCE_SUBMITTED))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Cannot transition dispute from OPEN to EVIDENCE_SUBMITTED");

        assertThatThrownBy(() -> stateMachine.validateTransition(
            DisputeStatus.EVIDENCE_REQUIRED, DisputeStatus.WON))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Cannot transition dispute from EVIDENCE_REQUIRED to WON");
    }

    @Test
    void shouldRejectTransitionsFromTerminalStates() {
        assertThatThrownBy(() -> stateMachine.validateTransition(DisputeStatus.WON, DisputeStatus.LOST))
            .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> stateMachine.validateTransition(DisputeStatus.LOST, DisputeStatus.WON))
            .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> stateMachine.validateTransition(DisputeStatus.CLOSED, DisputeStatus.OPEN))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldIdentifyTerminalStates() {
        assertThat(stateMachine.isTerminalState(DisputeStatus.WON)).isTrue();
        assertThat(stateMachine.isTerminalState(DisputeStatus.LOST)).isTrue();
        assertThat(stateMachine.isTerminalState(DisputeStatus.CLOSED)).isTrue();

        assertThat(stateMachine.isTerminalState(DisputeStatus.OPEN)).isFalse();
        assertThat(stateMachine.isTerminalState(DisputeStatus.EVIDENCE_REQUIRED)).isFalse();
        assertThat(stateMachine.isTerminalState(DisputeStatus.EVIDENCE_SUBMITTED)).isFalse();
    }

    @Test
    void shouldRejectNullCurrentStatus() {
        assertThatThrownBy(() -> stateMachine.validateTransition(null, DisputeStatus.OPEN))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Current dispute status cannot be null");
    }

    @Test
    void shouldRejectNullNewStatus() {
        assertThatThrownBy(() -> stateMachine.validateTransition(DisputeStatus.OPEN, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("New dispute status cannot be null");
    }
}
