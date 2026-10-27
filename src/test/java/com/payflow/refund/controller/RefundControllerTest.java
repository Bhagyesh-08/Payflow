package com.payflow.refund.controller;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.security.MerchantPrincipal;
import com.payflow.merchant.entity.Merchant;
import com.payflow.refund.dto.CreateRefundRequest;
import com.payflow.refund.dto.RefundResponse;
import com.payflow.refund.entity.Refund;
import com.payflow.refund.enums.RefundStatus;
import com.payflow.refund.mapper.RefundMapper;
import com.payflow.refund.repository.RefundRepository;
import com.payflow.refund.service.RefundService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundControllerTest {

    @Mock
    private RefundService refundService;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private RefundMapper mapper;

    @InjectMocks
    private RefundController refundController;

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
    void createRefund_DerivesMerchantFromPrincipal_WhenHeaderNotProvided() {
        setAuthenticatedMerchant(10L);

        CreateRefundRequest request = CreateRefundRequest.builder()
                .amount(new BigDecimal("50.00"))
                .currency("INR")
                .reason("Customer request")
                .build();

        Refund refund = Refund.builder().id(1L).refundId("REF_001").build();
        RefundResponse expectedResponse = RefundResponse.builder()
                .refundId("REF_001")
                .amount(new BigDecimal("50.00"))
                .status("REQUESTED")
                .build();

        when(refundService.createRefund(eq(100L), eq(10L), eq(request.getAmount()), eq("INR"), eq("Customer request"), eq("idem-key-1")))
                .thenReturn(refund);
        when(mapper.toResponse(refund)).thenReturn(expectedResponse);

        ResponseEntity<RefundResponse> response = refundController.createRefund(100L, null, "idem-key-1", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRefundId()).isEqualTo("REF_001");
        verify(refundService).createRefund(eq(100L), eq(10L), any(), any(), any(), eq("idem-key-1"));
    }

    @Test
    void createRefund_SucceedsWhenHeaderMatchesAuthenticatedMerchant() {
        setAuthenticatedMerchant(10L);

        CreateRefundRequest request = CreateRefundRequest.builder()
                .amount(new BigDecimal("50.00"))
                .currency("INR")
                .reason("Customer request")
                .build();

        Refund refund = Refund.builder().id(1L).refundId("REF_001").build();
        RefundResponse expectedResponse = RefundResponse.builder().refundId("REF_001").build();

        when(refundService.createRefund(eq(100L), eq(10L), any(), any(), any(), eq("idem-key-1")))
                .thenReturn(refund);
        when(mapper.toResponse(refund)).thenReturn(expectedResponse);

        ResponseEntity<RefundResponse> response = refundController.createRefund(100L, 10L, "idem-key-1", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(refundService).createRefund(eq(100L), eq(10L), any(), any(), any(), eq("idem-key-1"));
    }

    @Test
    void createRefund_ThrowsForbiddenWhenHeaderMismatchesPrincipal() {
        setAuthenticatedMerchant(10L);

        CreateRefundRequest request = CreateRefundRequest.builder()
                .amount(new BigDecimal("50.00"))
                .currency("INR")
                .reason("Customer request")
                .build();

        assertThatThrownBy(() -> refundController.createRefund(100L, 99L, "idem-key-1", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("X-Merchant-Id header does not match authenticated merchant");

        verifyNoInteractions(refundService);
    }

    @Test
    void getRefund_VerifiesMerchantOwnership_RejectsCrossMerchant() {
        setAuthenticatedMerchant(10L);

        Merchant otherMerchant = Merchant.builder().id(99L).build();
        Refund refund = Refund.builder().id(1L).refundId("REF_001").merchant(otherMerchant).build();

        when(refundService.getRefundById("REF_001")).thenReturn(refund);

        assertThatThrownBy(() -> refundController.getRefund("REF_001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot access resource owned by different merchant");

        verify(mapper, never()).toResponse(any());
    }

    @Test
    void getPaymentRefunds_VerifiesMerchantOwnership() {
        setAuthenticatedMerchant(10L);

        Merchant otherMerchant = Merchant.builder().id(99L).build();
        Refund refund = Refund.builder().id(1L).refundId("REF_001").merchant(otherMerchant).build();

        when(refundRepository.findByPaymentId(500L)).thenReturn(List.of(refund));

        assertThatThrownBy(() -> refundController.getPaymentRefunds(500L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot access resource owned by different merchant");
    }
}
