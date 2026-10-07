package com.payflow.reconciliation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartReconciliationRequest {
    
    @NotNull(message = "Gateway is required")
    private String gateway;
    
    @NotNull(message = "Merchant ID is required")
    private Long merchantId;
    
    @NotNull(message = "Business date is required")
    private LocalDate businessDate;
}
