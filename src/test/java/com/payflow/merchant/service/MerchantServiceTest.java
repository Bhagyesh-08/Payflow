package com.payflow.merchant.service;

import com.payflow.common.exception.DuplicateResourceException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.merchant.dto.CreateMerchantRequest;
import com.payflow.merchant.dto.MerchantResponse;
import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.enums.MerchantStatus;
import com.payflow.merchant.mapper.MerchantMapper;
import com.payflow.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantMapper merchantMapper;

    @InjectMocks
    private MerchantService merchantService;

    private CreateMerchantRequest request;
    private Merchant merchant;
    private MerchantResponse response;

    @BeforeEach
    void setUp() {
        request = CreateMerchantRequest.builder()
                .businessName("Test Business")
                .email("test@example.com")
                .phone("1234567890")
                .address("123 Test St")
                .build();

        merchant = Merchant.builder()
                .id(1L)
                .businessName("Test Business")
                .email("test@example.com")
                .merchantCode("MER_ABC123")
                .status(MerchantStatus.ACTIVE)
                .build();

        response = MerchantResponse.builder()
                .id(1L)
                .businessName("Test Business")
                .email("test@example.com")
                .merchantCode("MER_ABC123")
                .status(MerchantStatus.ACTIVE)
                .build();
    }

    @Test
    void createMerchant_Success() {
        when(merchantRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(merchantRepository.existsByBusinessName(request.getBusinessName())).thenReturn(false);
        when(merchantRepository.save(any(Merchant.class))).thenReturn(merchant);
        when(merchantMapper.toResponse(merchant)).thenReturn(response);

        MerchantResponse result = merchantService.createMerchant(request);

        assertThat(result).isNotNull();
        assertThat(result.getBusinessName()).isEqualTo("Test Business");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(merchantRepository, times(1)).save(any(Merchant.class));
    }

    @Test
    void createMerchant_DuplicateEmail_ThrowsException() {
        when(merchantRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> merchantService.createMerchant(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("email already exists");

        verify(merchantRepository, never()).save(any());
    }

    @Test
    void getMerchantById_Success() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(merchantMapper.toResponse(merchant)).thenReturn(response);

        MerchantResponse result = merchantService.getMerchantById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getMerchantById_NotFound_ThrowsException() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.getMerchantById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Merchant not found");
    }
}
