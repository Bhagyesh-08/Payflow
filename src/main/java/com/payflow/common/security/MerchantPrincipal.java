package com.payflow.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Authenticated Merchant Principal.
 * 
 * Represents authenticated merchant in security context.
 * Contains merchant ID for authorization checks.
 */
@Data
@AllArgsConstructor
public class MerchantPrincipal {
    private Long merchantId;
    private String merchantCode;
    private String businessName;
}
