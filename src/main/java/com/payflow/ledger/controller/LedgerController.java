package com.payflow.ledger.controller;

import com.payflow.ledger.dto.LedgerTransactionResponse;
import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.entity.LedgerTransaction;
import com.payflow.ledger.mapper.LedgerMapper;
import com.payflow.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Ledger REST API.
 * 
 * Read-only endpoints for ledger retrieval.
 * Ledger records are immutable - no mutation endpoints.
 */
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final LedgerMapper ledgerMapper;

    /**
     * Get ledger transaction by ID.
     */
    @GetMapping("/transactions/{id}")
    public ResponseEntity<LedgerTransactionResponse> getTransactionById(@PathVariable Long id) {
        LedgerTransaction transaction = ledgerService.getByPaymentId(String.valueOf(id));
        List<LedgerEntry> entries = ledgerService.getEntriesByTransactionId(transaction.getId());
        return ResponseEntity.ok(ledgerMapper.toResponse(transaction, entries));
    }

    /**
     * Get ledger transactions for a payment.
     */
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<LedgerTransactionResponse> getLedgerByPaymentId(@PathVariable String paymentId) {
        LedgerTransaction transaction = ledgerService.getByPaymentId(paymentId);
        List<LedgerEntry> entries = ledgerService.getEntriesByTransactionId(transaction.getId());
        return ResponseEntity.ok(ledgerMapper.toResponse(transaction, entries));
    }

    /**
     * Get all ledger transactions for a merchant.
     */
    @GetMapping("/merchants/{merchantId}")
    public ResponseEntity<List<LedgerTransactionResponse>> getLedgerByMerchantId(@PathVariable Long merchantId) {
        List<LedgerTransaction> transactions = ledgerService.getByMerchantId(merchantId);
        
        List<LedgerTransactionResponse> responses = transactions.stream()
            .map(tx -> {
                List<LedgerEntry> entries = ledgerService.getEntriesByTransactionId(tx.getId());
                return ledgerMapper.toResponse(tx, entries);
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
}
