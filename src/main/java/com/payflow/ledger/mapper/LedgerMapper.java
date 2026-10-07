package com.payflow.ledger.mapper;

import com.payflow.ledger.dto.LedgerEntryResponse;
import com.payflow.ledger.dto.LedgerTransactionResponse;
import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.entity.LedgerTransaction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class LedgerMapper {

    public LedgerTransactionResponse toResponse(LedgerTransaction transaction, List<LedgerEntry> entries) {
        return LedgerTransactionResponse.builder()
            .id(transaction.getId())
            .transactionReference(transaction.getTransactionReference())
            .paymentId(transaction.getPaymentId())
            .merchantId(transaction.getMerchantId())
            .transactionType(transaction.getTransactionType())
            .currency(transaction.getCurrency())
            .amount(transaction.getAmount())
            .description(transaction.getDescription())
            .createdAt(transaction.getCreatedAt())
            .entries(entries.stream().map(this::toEntryResponse).collect(Collectors.toList()))
            .build();
    }

    public LedgerEntryResponse toEntryResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
            .id(entry.getId())
            .accountId(entry.getAccount().getId())
            .accountCode(entry.getAccount().getAccountCode())
            .accountType(entry.getAccount().getAccountType().name())
            .entryType(entry.getEntryType().name())
            .amount(entry.getAmount())
            .currency(entry.getCurrency())
            .reference(entry.getReference())
            .createdAt(entry.getCreatedAt())
            .build();
    }
}
