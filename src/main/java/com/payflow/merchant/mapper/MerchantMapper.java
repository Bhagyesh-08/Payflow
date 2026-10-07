package com.payflow.merchant.mapper;

import com.payflow.merchant.dto.MerchantResponse;
import com.payflow.merchant.entity.Merchant;
import org.springframework.stereotype.Component;

@Component
public class MerchantMapper {

    public MerchantResponse toResponse(Merchant merchant) {
        return MerchantResponse.builder()
                .id(merchant.getId())
                .businessName(merchant.getBusinessName())
                .email(merchant.getEmail())
                .phone(merchant.getPhone())
                .merchantCode(merchant.getMerchantCode())
                .status(merchant.getStatus())
                .address(merchant.getAddress())
                .apiKey(merchant.getApiKey())
                .createdAt(merchant.getCreatedAt())
                .updatedAt(merchant.getUpdatedAt())
                .build();
    }
}
