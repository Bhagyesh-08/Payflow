package com.payflow.reconciliation.controller;

import com.payflow.reconciliation.dto.ReconciliationRecordResponse;
import com.payflow.reconciliation.dto.ReconciliationRunResponse;
import com.payflow.reconciliation.dto.StartReconciliationRequest;
import com.payflow.reconciliation.entity.ReconciliationRecord;
import com.payflow.reconciliation.entity.ReconciliationRun;
import com.payflow.reconciliation.mapper.ReconciliationMapper;
import com.payflow.reconciliation.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@Tag(name = "Reconciliation", description = "Payment reconciliation with gateway reports")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationMapper mapper;

    /**
     * Starts a reconciliation run.
     */
    @PostMapping("/runs")
    @Operation(summary = "Start a new reconciliation run")
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
    @Operation(summary = "Get reconciliation run by ID")
    public ResponseEntity<ReconciliationRunResponse> getRunById(@PathVariable Long id) {
        ReconciliationRun run = reconciliationService.getRunById(id);
        return ResponseEntity.ok(mapper.toRunResponse(run));
    }

    /**
     * Retrieves reconciliation records for a run.
     */
    @GetMapping("/runs/{id}/records")
    @Operation(summary = "Get reconciliation records for a run")
    public ResponseEntity<List<ReconciliationRecordResponse>> getRecordsByRunId(@PathVariable Long id) {
        List<ReconciliationRecord> records = reconciliationService.getRecordsByRunId(id);
        List<ReconciliationRecordResponse> responses = records.stream()
            .map(mapper::toRecordResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves all reconciliation runs for a merchant (paginated).
     */
    @GetMapping("/merchants/{merchantId}/runs")
    @Operation(summary = "Get paginated reconciliation runs for a merchant")
    public ResponseEntity<Page<ReconciliationRunResponse>> getMerchantRuns(
            @PathVariable Long merchantId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<ReconciliationRun> runs = reconciliationService.getRunsByMerchantId(merchantId, pageable);
        Page<ReconciliationRunResponse> responses = runs.map(mapper::toRunResponse);
        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves discrepancies for a merchant (paginated).
     */
    @GetMapping("/merchants/{merchantId}/discrepancies")
    @Operation(summary = "Get paginated discrepancies for a merchant")
    public ResponseEntity<Page<ReconciliationRecordResponse>> getMerchantDiscrepancies(
            @PathVariable Long merchantId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReconciliationRecord> discrepancies = reconciliationService.getDiscrepancies(merchantId, pageable);
        Page<ReconciliationRecordResponse> responses = discrepancies.map(mapper::toRecordResponse);
        return ResponseEntity.ok(responses);
    }
}
