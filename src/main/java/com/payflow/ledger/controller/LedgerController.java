package com.payflow.ledger.controller;

import com.payflow.common.security.SecurityUtils;
import com.payflow.ledger.dto.LedgerTransactionResponse;
import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.entity.LedgerTransaction;
import com.payflow.ledger.mapper.LedgerMapper;
import com.payflow.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ledger REST API.
 * 
 * Read-only endpoints for ledger retrieval.
 * Ledger records are immutable - no mutation endpoints.
 */
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Tag(name = "Ledger", description = "Immutable double-entry ledger for payment transactions")
public class LedgerController {

    private final LedgerService ledgerService;
    private final LedgerMapper ledgerMapper;

    /**
     * Get ledger transaction by internal ID.
     */
    @GetMapping("/transactions/{id}")
    @Operation(summary = "Get ledger transaction by ID")
    public ResponseEntity<LedgerTransactionResponse> getTransactionById(@PathVariable Long id) {
        LedgerTransaction transaction = ledgerService.getById(id);
        SecurityUtils.validateMerchantAccess(transaction.getMerchantId());
        List<LedgerEntry> entries = ledgerService.getEntriesByTransactionId(transaction.getId());
        return ResponseEntity.ok(ledgerMapper.toResponse(transaction, entries));
    }

    /**
     * Get ledger transactions for a payment.
     */
    @GetMapping("/payments/{paymentId}")
    @Operation(summary = "Get ledger transactions for a payment")
    public ResponseEntity<LedgerTransactionResponse> getLedgerByPaymentId(@PathVariable String paymentId) {
        LedgerTransaction transaction = ledgerService.getByPaymentId(paymentId);
        SecurityUtils.validateMerchantAccess(transaction.getMerchantId());
        List<LedgerEntry> entries = ledgerService.getEntriesByTransactionId(transaction.getId());
        return ResponseEntity.ok(ledgerMapper.toResponse(transaction, entries));
    }

    /**
     * Get all ledger transactions for a merchant (paginated).
     */
    @GetMapping("/merchants/{merchantId}")
    @Operation(summary = "Get paginated ledger transactions for a merchant")
    public ResponseEntity<Page<LedgerTransactionResponse>> getLedgerByMerchantId(
            @PathVariable Long merchantId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        
        SecurityUtils.validateMerchantAccess(merchantId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<LedgerTransaction> transactions = ledgerService.getByMerchantId(merchantId, pageable);
        
        Page<LedgerTransactionResponse> responses = transactions.map(tx -> {
            List<LedgerEntry> entries = ledgerService.getEntriesByTransactionId(tx.getId());
            return ledgerMapper.toResponse(tx, entries);
        });
        
        return ResponseEntity.ok(responses);
    }
}
