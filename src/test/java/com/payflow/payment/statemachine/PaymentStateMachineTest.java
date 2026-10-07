package com.payflow.payment.statemachine;

import com.payflow.common.exception.BusinessException;
import com.payflow.payment.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PaymentStateMachineTest {

    private PaymentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new PaymentStateMachine();
    }

    // Valid Transitions Tests

    @Test
    void validateTransition_CreatedToPaymentInitiated_Success() {
        assertThatNoException().isThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.PAYMENT_INITIATED)
        );
    }

    @Test
    void validateTransition_CreatedToFailed_Success() {
        assertThatNoException().isThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.FAILED)
        );
    }

    @Test
    void validateTransition_PaymentInitiatedToAuthorized_Success() {
        assertThatNoException().isThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.PAYMENT_INITIATED, PaymentStatus.AUTHORIZED)
        );
    }

    @Test
    void validateTransition_PaymentInitiatedToFailed_Success() {
        assertThatNoException().isThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.PAYMENT_INITIATED, PaymentStatus.FAILED)
        );
    }

    @Test
    void validateTransition_AuthorizedToCaptured_Success() {
        assertThatNoException().isThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED)
        );
    }

    @Test
    void validateTransition_AuthorizedToFailed_Success() {
        assertThatNoException().isThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED)
        );
    }

    @Test
    void validateTransition_SameState_Success() {
        // Same state transition is allowed (idempotent operations)
        assertThatNoException().isThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.CREATED)
        );
        assertThatNoException().isThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.AUTHORIZED)
        );
    }

    // Invalid Transitions Tests

    @Test
    void validateTransition_CreatedToAuthorized_ThrowsException() {
        assertThatThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.AUTHORIZED)
        )
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Cannot transition payment from CREATED to AUTHORIZED");
    }

    @Test
    void validateTransition_CreatedToCaptured_ThrowsException() {
        assertThatThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.CAPTURED)
        )
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Cannot transition payment from CREATED to CAPTURED");
    }

    @Test
    void validateTransition_PaymentInitiatedToCaptured_ThrowsException() {
        assertThatThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.PAYMENT_INITIATED, PaymentStatus.CAPTURED)
        )
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Cannot transition payment from PAYMENT_INITIATED to CAPTURED");
    }

    @Test
    void validateTransition_AuthorizedToPaymentInitiated_ThrowsException() {
        assertThatThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.PAYMENT_INITIATED)
        )
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Cannot transition payment from AUTHORIZED to PAYMENT_INITIATED");
    }

    // Terminal State Tests

    @Test
    void validateTransition_CapturedToAnyState_ThrowsException() {
        assertThatThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.CAPTURED, PaymentStatus.PAYMENT_INITIATED)
        )
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Cannot transition payment from CAPTURED");

        assertThatThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.CAPTURED, PaymentStatus.AUTHORIZED)
        )
        .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.CAPTURED, PaymentStatus.FAILED)
        )
        .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateTransition_FailedToAnyState_ThrowsException() {
        assertThatThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.FAILED, PaymentStatus.CREATED)
        )
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Cannot transition payment from FAILED");

        assertThatThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.FAILED, PaymentStatus.PAYMENT_INITIATED)
        )
        .isInstanceOf(BusinessException.class);
    }

    @Test
    void isTerminalState_CapturedAndFailed_ReturnsTrue() {
        assertThat(stateMachine.isTerminalState(PaymentStatus.CAPTURED)).isTrue();
        assertThat(stateMachine.isTerminalState(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void isTerminalState_NonTerminalStates_ReturnsFalse() {
        assertThat(stateMachine.isTerminalState(PaymentStatus.CREATED)).isFalse();
        assertThat(stateMachine.isTerminalState(PaymentStatus.PAYMENT_INITIATED)).isFalse();
        assertThat(stateMachine.isTerminalState(PaymentStatus.AUTHORIZED)).isFalse();
    }

    // Null Handling Tests

    @Test
    void validateTransition_NullCurrentStatus_ThrowsException() {
        assertThatThrownBy(() ->
            stateMachine.validateTransition(null, PaymentStatus.CREATED)
        )
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Current payment status cannot be null");
    }

    @Test
    void validateTransition_NullNewStatus_ThrowsException() {
        assertThatThrownBy(() ->
            stateMachine.validateTransition(PaymentStatus.CREATED, null)
        )
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("New payment status cannot be null");
    }

    // Get Valid Next Statuses Tests

    @Test
    void getValidNextStatuses_Created_ReturnsCorrectSet() {
        Set<PaymentStatus> validStatuses = stateMachine.getValidNextStatuses(PaymentStatus.CREATED);
        
        assertThat(validStatuses)
            .hasSize(2)
            .contains(PaymentStatus.PAYMENT_INITIATED, PaymentStatus.FAILED);
    }

    @Test
    void getValidNextStatuses_PaymentInitiated_ReturnsCorrectSet() {
        Set<PaymentStatus> validStatuses = stateMachine.getValidNextStatuses(PaymentStatus.PAYMENT_INITIATED);
        
        assertThat(validStatuses)
            .hasSize(2)
            .contains(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED);
    }

    @Test
    void getValidNextStatuses_Authorized_ReturnsCorrectSet() {
        Set<PaymentStatus> validStatuses = stateMachine.getValidNextStatuses(PaymentStatus.AUTHORIZED);
        
        assertThat(validStatuses)
            .hasSize(2)
            .contains(PaymentStatus.CAPTURED, PaymentStatus.FAILED);
    }

    @Test
    void getValidNextStatuses_Captured_ReturnsEmptySet() {
        Set<PaymentStatus> validStatuses = stateMachine.getValidNextStatuses(PaymentStatus.CAPTURED);
        
        assertThat(validStatuses).isEmpty();
    }

    @Test
    void getValidNextStatuses_Failed_ReturnsEmptySet() {
        Set<PaymentStatus> validStatuses = stateMachine.getValidNextStatuses(PaymentStatus.FAILED);
        
        assertThat(validStatuses).isEmpty();
    }
}
