package com.payflow.ledger.controller;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.security.MerchantPrincipal;
import com.payflow.ledger.dto.LedgerTransactionResponse;
import com.payflow.ledger.entity.LedgerTransaction;
import com.payflow.ledger.mapper.LedgerMapper;
import com.payflow.ledger.service.LedgerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerControllerTest {

    @Mock
    private LedgerService ledgerService;

    @Mock
    private LedgerMapper ledgerMapper;

    @InjectMocks
    private LedgerController ledgerController;

    private void setAuthenticatedMerchant(Long merchantId) {
        MerchantPrincipal principal = new MerchantPrincipal(merchantId, "MER_" + merchantId, "Merchant " + merchantId);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getTransactionById_SearchesByInternalTransactionId_NotPaymentId() {
        setAuthenticatedMerchant(10L);

        Long internalTxId = 555L;
        LedgerTransaction tx = LedgerTransaction.builder()
                .id(internalTxId)
                .transactionReference("PAYMENT_CAPTURE_PAY_123")
                .paymentId("PAY_123")
                .merchantId(10L)
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .build();

        LedgerTransactionResponse expectedResponse = LedgerTransactionResponse.builder()
                .id(internalTxId)
                .paymentId("PAY_123")
                .build();

        when(ledgerService.getById(internalTxId)).thenReturn(tx);
        when(ledgerService.getEntriesByTransactionId(internalTxId)).thenReturn(Collections.emptyList());
        when(ledgerMapper.toResponse(tx, Collections.emptyList())).thenReturn(expectedResponse);

        ResponseEntity<LedgerTransactionResponse> response = ledgerController.getTransactionById(internalTxId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(internalTxId);

        // Verify getById was called with the numeric primary key, NOT getByPaymentId
        verify(ledgerService).getById(eq(555L));
        verify(ledgerService, never()).getByPaymentId(any());
    }

    @Test
    void getTransactionById_NotFound_ThrowsLedgerNotFound() {
        setAuthenticatedMerchant(10L);

        when(ledgerService.getById(999L))
                .thenThrow(new BusinessException("LEDGER_NOT_FOUND", "Ledger transaction not found: 999"));

        assertThatThrownBy(() -> ledgerController.getTransactionById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ledger transaction not found: 999");
    }

    @Test
    void getTransactionById_CrossMerchant_ThrowsForbidden() {
        setAuthenticatedMerchant(10L);

        LedgerTransaction tx = LedgerTransaction.builder()
                .id(555L)
                .merchantId(99L) // Different merchant
                .build();

        when(ledgerService.getById(555L)).thenReturn(tx);

        assertThatThrownBy(() -> ledgerController.getTransactionById(555L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot access resource owned by different merchant");
    }

    @Test
    void getLedgerByPaymentId_SearchesByPaymentId() {
        setAuthenticatedMerchant(10L);

        String paymentId = "PAY_XYZ_789";
        LedgerTransaction tx = LedgerTransaction.builder()
                .id(100L)
                .paymentId(paymentId)
                .merchantId(10L)
                .build();

        LedgerTransactionResponse expectedResponse = LedgerTransactionResponse.builder()
                .id(100L)
                .paymentId(paymentId)
                .build();

        when(ledgerService.getByPaymentId(paymentId)).thenReturn(tx);
        when(ledgerService.getEntriesByTransactionId(100L)).thenReturn(Collections.emptyList());
        when(ledgerMapper.toResponse(tx, Collections.emptyList())).thenReturn(expectedResponse);

        ResponseEntity<LedgerTransactionResponse> response = ledgerController.getLedgerByPaymentId(paymentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPaymentId()).isEqualTo(paymentId);
        verify(ledgerService).getByPaymentId(eq("PAY_XYZ_789"));
    }

    @Test
    void getLedgerByPaymentId_NotFound_ThrowsLedgerNotFound() {
        setAuthenticatedMerchant(10L);

        when(ledgerService.getByPaymentId("PAY_MISSING"))
                .thenThrow(new BusinessException("LEDGER_NOT_FOUND", "Ledger transaction not found for payment: PAY_MISSING"));

        assertThatThrownBy(() -> ledgerController.getLedgerByPaymentId("PAY_MISSING"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ledger transaction not found for payment: PAY_MISSING");
    }
}
