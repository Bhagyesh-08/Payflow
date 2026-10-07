package com.payflow.reconciliation.controller;

import com.payflow.reconciliation.dto.ReconciliationRecordResponse;
import com.payflow.reconciliation.dto.ReconciliationRunResponse;
import com.payflow.reconciliation.dto.StartReconciliationRequest;
import com.payflow.reconciliation.entity.ReconciliationRecord;
import com.payflow.reconciliation.entity.ReconciliationRun;
import com.payflow.reconciliation.mapper.ReconciliationMapper;
import com.payflow.reconciliation.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Reconciliation REST API.
 * 
 * Provides endpoints for:
 * - Starting reconciliation runs
 * - Retrieving run results
 * - Viewing discrepancies
 * 
 * Read-focused API. No endpoints for modifying reconciliation records.
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationMapper mapper;

    /**
     * Starts a reconciliation run.
     */
    @PostMapping("/runs")
    public ResponseEntity<ReconciliationRunResponse> startReconciliation(
            @Valid @RequestBody StartReconciliationRequest request) {
        
        ReconciliationRun run = reconciliationService.startReconciliationRun(
            request.getMerchantId(),
            request.getGateway(),
            request.getBusinessDate()
        );
        
        return ResponseEntity.ok(mapper.toRunResponse(run));
    }

    /**
     * Retrieves reconciliation run by ID.
     */
    @GetMapping("/runs/{id}")
    public ResponseEntity<ReconciliationRunResponse> getRunById(@PathVariable Long id) {
        ReconciliationRun run = reconciliationService.getRunById(id);
        return ResponseEntity.ok(mapper.toRunResponse(run));
    }

    /**
     * Retrieves reconciliation records for a run.
     */
    @GetMapping("/runs/{id}/records")
    public ResponseEntity<List<ReconciliationRecordResponse>> getRecordsByRunId(@PathVariable Long id) {
        List<ReconciliationRecord> records = reconciliationService.getRecordsByRunId(id);
        List<ReconciliationRecordResponse> responses = records.stream()
            .map(mapper::toRecordResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves all reconciliation runs for a merchant.
     */
    @GetMapping("/merchants/{merchantId}/runs")
    public ResponseEntity<List<ReconciliationRunResponse>> getMerchantRuns(@PathVariable Long merchantId) {
        List<ReconciliationRun> runs = reconciliationService.getRunsByMerchantId(merchantId);
        List<ReconciliationRunResponse> responses = runs.stream()
            .map(mapper::toRunResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves discrepancies for a merchant.
     */
    @GetMapping("/merchants/{merchantId}/discrepancies")
    public ResponseEntity<List<ReconciliationRecordResponse>> getMerchantDiscrepancies(@PathVariable Long merchantId) {
        List<ReconciliationRecord> discrepancies = reconciliationService.getDiscrepancies(merchantId);
        List<ReconciliationRecordResponse> responses = discrepancies.stream()
            .map(mapper::toRecordResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
