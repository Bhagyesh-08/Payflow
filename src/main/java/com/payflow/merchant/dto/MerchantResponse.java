package com.payflow.merchant.dto;

import com.payflow.merchant.enums.MerchantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantResponse {
    private Long id;
    private String businessName;
    private String email;
    private String phone;
    private String merchantCode;
    private MerchantStatus status;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
