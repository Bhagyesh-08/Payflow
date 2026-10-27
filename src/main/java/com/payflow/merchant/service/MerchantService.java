package com.payflow.merchant.service;

import com.payflow.common.exception.DuplicateResourceException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.util.IdGenerator;
import com.payflow.merchant.dto.CreateMerchantRequest;
import com.payflow.merchant.dto.MerchantRegistrationResponse;
import com.payflow.merchant.dto.MerchantResponse;
import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.enums.MerchantStatus;
import com.payflow.merchant.mapper.MerchantMapper;
import com.payflow.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantMapper merchantMapper;

    @Transactional
    public MerchantRegistrationResponse createMerchant(CreateMerchantRequest request) {
        log.info("Creating merchant with email: {}", request.getEmail());

        if (merchantRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Merchant with email already exists: " + request.getEmail());
        }

        if (merchantRepository.existsByBusinessName(request.getBusinessName())) {
            throw new DuplicateResourceException("Merchant with business name already exists: " + request.getBusinessName());
        }

        Merchant merchant = Merchant.builder()
                .businessName(request.getBusinessName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .merchantCode(IdGenerator.generateMerchantCode())
                .apiKey(IdGenerator.generateApiKey())
                .status(MerchantStatus.ACTIVE)
                .build();

        Merchant saved = merchantRepository.save(merchant);
        log.info("Merchant created successfully with code: {}", saved.getMerchantCode());

        return merchantMapper.toRegistrationResponse(saved);
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchantById(Long id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found with id: " + id));
        return merchantMapper.toResponse(merchant);
    }

    @Transactional(readOnly = true)
    public Merchant getMerchantEntityById(Long id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found with id: " + id));
    }
}
