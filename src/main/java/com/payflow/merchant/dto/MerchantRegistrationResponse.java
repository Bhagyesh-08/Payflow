package com.payflow.merchant.dto;

import com.payflow.merchant.enums.MerchantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Merchant Registration Response.
 * 
 * Returned ONLY upon initial merchant registration/creation.
 * Contains the generated API key as a one-time secret.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRegistrationResponse {
    private Long id;
    private String businessName;
    private String email;
    private String phone;
    private String merchantCode;
    private MerchantStatus status;
    private String address;
    private String apiKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
