package com.payflow.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransactionResponse {
    private Long id;
    private String transactionReference;
    private String paymentId;
    private Long merchantId;
    private String transactionType;
    private String currency;
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;
    private List<LedgerEntryResponse> entries;
}
