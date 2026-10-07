package com.payflow.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationRecordResponse {
    private Long id;
    private Long runId;
    private String gateway;
    private String gatewayReference;
    private String paymentId;
    private BigDecimal internalAmount;
    private BigDecimal gatewayAmount;
    private String currency;
    private String internalStatus;
    private String gatewayStatus;
    private String result;
    private String discrepancyType;
    private LocalDateTime reconciledAt;
    private String notes;
}
