package com.payflow.payment.controller;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.security.MerchantPrincipal;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.enums.PaymentStatus;
import com.payflow.payment.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

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
    void getPayments_ReturnsPaginatedPaymentsForAuthenticatedMerchant() {
        setAuthenticatedMerchant(42L);

        PaymentResponse p1 = PaymentResponse.builder()
                .id(101L)
                .paymentId("PAY_001")
                .amount(new BigDecimal("150.00"))
                .currency("INR")
                .status(PaymentStatus.CAPTURED)
                .createdAt(LocalDateTime.now())
                .build();

        Page<PaymentResponse> expectedPage = new PageImpl<>(List.of(p1));
        when(paymentService.getPaymentsForMerchant(eq(42L), any(Pageable.class))).thenReturn(expectedPage);

        ResponseEntity<Page<PaymentResponse>> response = paymentController.getPayments(0, 20, "createdAt", Sort.Direction.DESC);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getContent().get(0).getPaymentId()).isEqualTo("PAY_001");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentService).getPaymentsForMerchant(eq(42L), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(0);
        assertThat(captured.getPageSize()).isEqualTo(20);
        assertThat(captured.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captured.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getPayments_EmptyPage_ReturnsEmptyPage() {
        setAuthenticatedMerchant(42L);

        Page<PaymentResponse> emptyPage = new PageImpl<>(Collections.emptyList());
        when(paymentService.getPaymentsForMerchant(eq(42L), any(Pageable.class))).thenReturn(emptyPage);

        ResponseEntity<Page<PaymentResponse>> response = paymentController.getPayments(0, 20, "createdAt", Sort.Direction.DESC);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();
    }

    @Test
    void getPayments_NegativePage_ThrowsInvalidPageException() {
        setAuthenticatedMerchant(42L);

        assertThatThrownBy(() -> paymentController.getPayments(-1, 20, "createdAt", Sort.Direction.DESC))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Page index must not be negative");

        verifyNoInteractions(paymentService);
    }

    @Test
    void getPayments_ZeroOrNegativeSize_ThrowsInvalidPageSizeException() {
        setAuthenticatedMerchant(42L);

        assertThatThrownBy(() -> paymentController.getPayments(0, 0, "createdAt", Sort.Direction.DESC))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Page size must be greater than zero");

        verifyNoInteractions(paymentService);
    }

    @Test
    void getPayments_SizeExceeding100_ThrowsInvalidPageSizeException() {
        setAuthenticatedMerchant(42L);

        assertThatThrownBy(() -> paymentController.getPayments(0, 101, "createdAt", Sort.Direction.DESC))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Page size must not exceed 100");

        verifyNoInteractions(paymentService);
    }

    @Test
    void getPayments_InvalidSortField_ThrowsInvalidSortFieldException() {
        setAuthenticatedMerchant(42L);

        assertThatThrownBy(() -> paymentController.getPayments(0, 20, "unsupportedColumn", Sort.Direction.DESC))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Sort field 'unsupportedColumn' is not supported");

        verifyNoInteractions(paymentService);
    }

    @Test
    void getPayments_DerivesMerchantFromPrincipal_EnforcesDataIsolation() {
        // Authenticated as Merchant 99
        setAuthenticatedMerchant(99L);

        when(paymentService.getPaymentsForMerchant(eq(99L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        paymentController.getPayments(0, 10, "amount", Sort.Direction.ASC);

        // Verify it queried specifically for merchant 99, never merchant 1 or 42
        verify(paymentService).getPaymentsForMerchant(eq(99L), any(Pageable.class));
        verify(paymentService, never()).getPaymentsForMerchant(eq(1L), any(Pageable.class));
    }
}
