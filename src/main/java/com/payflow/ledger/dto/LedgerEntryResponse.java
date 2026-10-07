package com.payflow.ledger.dto;

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
public class LedgerEntryResponse {
    private Long id;
    private Long accountId;
    private String accountCode;
    private String accountType;
    private String entryType;
    private BigDecimal amount;
    private String currency;
    private String reference;
    private LocalDateTime createdAt;
}
