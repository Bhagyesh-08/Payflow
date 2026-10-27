package com.payflow.merchant.controller;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.security.MerchantPrincipal;
import com.payflow.merchant.dto.CreateMerchantRequest;
import com.payflow.merchant.dto.MerchantRegistrationResponse;
import com.payflow.merchant.dto.MerchantResponse;
import com.payflow.merchant.enums.MerchantStatus;
import com.payflow.merchant.service.MerchantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantControllerSecurityTest {

    @Mock
    private MerchantService merchantService;

    @InjectMocks
    private MerchantController merchantController;

    private void setAuthenticatedMerchant(Long merchantId) {
        MerchantPrincipal principal = new MerchantPrincipal(merchantId, "MER_TEST", "Test Merchant");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMerchantById_DoesNotExposeApiKey() throws Exception {
        setAuthenticatedMerchant(1L);

        MerchantResponse responseDto = MerchantResponse.builder()
                .id(1L)
                .businessName("Test Merchant")
                .email("test@merchant.com")
                .merchantCode("MER_TEST")
                .status(MerchantStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        when(merchantService.getMerchantById(1L)).thenReturn(responseDto);

        ResponseEntity<MerchantResponse> response = merchantController.getMerchantById(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(1L);
        // Verify via reflection / compilation that MerchantResponse has no apiKey field or value
        assertThat(response.getBody().getClass().getDeclaredFields())
                .noneMatch(field -> field.getName().equalsIgnoreCase("apiKey"));
    }

    @Test
    void getMerchantById_CrossMerchantAccess_ThrowsForbidden() {
        // Authenticated as merchant 2, trying to access merchant 1
        setAuthenticatedMerchant(2L);

        assertThatThrownBy(() -> merchantController.getMerchantById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot access resource owned by different merchant");

        verify(merchantService, never()).getMerchantById(any());
    }

    @Test
    void createMerchant_ReturnsOneTimeApiKey() {
        CreateMerchantRequest request = CreateMerchantRequest.builder()
                .businessName("New Merchant")
                .email("new@merchant.com")
                .build();

        MerchantRegistrationResponse regResponse = MerchantRegistrationResponse.builder()
                .id(10L)
                .businessName("New Merchant")
                .email("new@merchant.com")
                .merchantCode("MER_NEW10")
                .apiKey("ak_live_one_time_secret_xyz")
                .status(MerchantStatus.ACTIVE)
                .build();

        when(merchantService.createMerchant(request)).thenReturn(regResponse);

        ResponseEntity<MerchantRegistrationResponse> response = merchantController.createMerchant(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getApiKey()).isEqualTo("ak_live_one_time_secret_xyz");
    }
}
