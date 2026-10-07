package com.payflow.reconciliation.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.reconciliation.entity.ReconciliationRecord;
import com.payflow.reconciliation.entity.ReconciliationRun;
import com.payflow.reconciliation.enums.DiscrepancyType;
import com.payflow.reconciliation.enums.ReconciliationResult;
import com.payflow.reconciliation.enums.ReconciliationRunStatus;
import com.payflow.reconciliation.model.GatewayPaymentRecord;
import com.payflow.reconciliation.provider.ReconciliationDataException;
import com.payflow.reconciliation.provider.ReconciliationDataProvider;
import com.payflow.reconciliation.repository.ReconciliationRecordRepository;
import com.payflow.reconciliation.repository.ReconciliationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reconciliation Service.
 * 
 * Compares internal payment records against gateway payment records.
 * Detects discrepancies but does NOT modify historical ledger entries.
 * 
 * Matching Strategy:
 * 1. Match by gatewayPaymentId (strongest identifier)
 * 2. Verify amount, currency, status
 * 3. Classify as MATCHED, DISCREPANCY, or UNRESOLVED
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationRecordRepository recordRepository;
    private final PaymentRepository paymentRepository;
    private final ReconciliationDataProvider dataProvider;

    /**
     * Starts reconciliation run for merchant/gateway/date.
     * 
     * @param merchantId Merchant identifier
     * @param gateway Gateway name
     * @param businessDate Business date to reconcile
     * @return Reconciliation run
     */
    @Transactional
    public ReconciliationRun startReconciliationRun(Long merchantId, String gateway, LocalDate businessDate) {
        log.info("Starting reconciliation: merchant={}, gateway={}, date={}", 
            merchantId, gateway, businessDate);

        // Check if provider is available
        if (!dataProvider.isAvailable()) {
            throw new BusinessException("PROVIDER_UNAVAILABLE", 
                "Reconciliation provider for " + gateway + " is not available");
        }

        // Check for existing run (idempotency)
        Optional<ReconciliationRun> existing = runRepository
            .findByMerchantIdAndGatewayAndBusinessDate(merchantId, gateway, businessDate);
        
        if (existing.isPresent()) {
            log.info("Reconciliation run already exists: id={}", existing.get().getId());
            return existing.get();
        }

        // Create run
        ReconciliationRun run = ReconciliationRun.builder()
            .merchantId(merchantId)
            .gateway(gateway)
            .businessDate(businessDate)
            .startedAt(LocalDateTime.now())
            .status(ReconciliationRunStatus.STARTED)
            .totalRecords(0)
            .matchedRecords(0)
            .discrepancyRecords(0)
            .unresolvedRecords(0)
            .build();

        run = runRepository.save(run);

        try {
            // Fetch gateway records
            List<GatewayPaymentRecord> gatewayRecords = dataProvider.getPaymentRecords(merchantId, businessDate);
            log.info("Retrieved {} gateway records", gatewayRecords.size());

            // Process each gateway record
            List<ReconciliationRecord> records = new ArrayList<>();
            for (GatewayPaymentRecord gatewayRecord : gatewayRecords) {
                ReconciliationRecord record = reconcileRecord(run, gatewayRecord, merchantId);
                records.add(record);
            }

            recordRepository.saveAll(records);

            // Calculate summary
            int matched = (int) records.stream().filter(r -> r.getResult() == ReconciliationResult.MATCHED).count();
            int discrepancies = (int) records.stream().filter(r -> r.getResult() == ReconciliationResult.DISCREPANCY).count();
            int unresolved = (int) records.stream().filter(r -> r.getResult() == ReconciliationResult.UNRESOLVED).count();

            run.setTotalRecords(records.size());
            run.setMatchedRecords(matched);
            run.setDiscrepancyRecords(discrepancies);
            run.setUnresolvedRecords(unresolved);
            run.setStatus(ReconciliationRunStatus.COMPLETED);
            run.setCompletedAt(LocalDateTime.now());

            run = runRepository.save(run);

            log.info("Reconciliation completed: run_id={}, total={}, matched={}, discrepancies={}", 
                run.getId(), records.size(), matched, discrepancies);

            return run;

        } catch (ReconciliationDataException e) {
            log.error("Gateway data retrieval failed", e);
            run.setStatus(ReconciliationRunStatus.FAILED);
            run.setErrorMessage("Gateway data retrieval failed: " + e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            throw new BusinessException("RECON_FAILED", "Reconciliation failed: " + e.getMessage());

        } catch (Exception e) {
            log.error("Reconciliation processing failed", e);
            run.setStatus(ReconciliationRunStatus.FAILED);
            run.setErrorMessage("Processing failed: " + e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            throw new BusinessException("RECON_FAILED", "Reconciliation failed: " + e.getMessage());
        }
    }

    /**
     * Reconciles single gateway record against internal data.
     */
    private ReconciliationRecord reconcileRecord(
            ReconciliationRun run, 
            GatewayPaymentRecord gatewayRecord, 
            Long merchantId) {

        String gatewayRef = gatewayRecord.getGatewayReference();
        
        // Find internal payment by gateway reference
        Optional<Payment> paymentOpt = paymentRepository.findByGatewayPaymentId(gatewayRef);

        if (!paymentOpt.isPresent()) {
            // Rule D: Gateway record without internal payment
            return createDiscrepancyRecord(run, gatewayRecord, null, 
                DiscrepancyType.MISSING_INTERNAL_PAYMENT,
                "Gateway payment not found in internal system");
        }

        Payment payment = paymentOpt.get();

        // Verify merchant ownership
        if (!payment.getMerchant().getId().equals(merchantId)) {
            log.warn("Merchant mismatch: gateway_ref={}, expected_merchant={}, actual_merchant={}", 
                gatewayRef, merchantId, payment.getMerchant().getId());
            return createDiscrepancyRecord(run, gatewayRecord, payment,
                DiscrepancyType.MISSING_INTERNAL_PAYMENT,
                "Payment belongs to different merchant");
        }

        // Rule B: Amount mismatch
        if (payment.getAmount().compareTo(gatewayRecord.getAmount()) != 0) {
            return createDiscrepancyRecord(run, gatewayRecord, payment,
                DiscrepancyType.AMOUNT_MISMATCH,
                String.format("Amount mismatch: internal=%s, gateway=%s", 
                    payment.getAmount(), gatewayRecord.getAmount()));
        }

        // Rule C: Currency mismatch
        if (!payment.getCurrency().equals(gatewayRecord.getCurrency())) {
            return createDiscrepancyRecord(run, gatewayRecord, payment,
                DiscrepancyType.CURRENCY_MISMATCH,
                String.format("Currency mismatch: internal=%s, gateway=%s", 
                    payment.getCurrency(), gatewayRecord.getCurrency()));
        }

        // Rule F: Status mismatch (simplified - gateway "captured" should match internal "CAPTURED")
        String internalStatus = payment.getStatus().name();
        String gatewayStatus = normalizeGatewayStatus(gatewayRecord.getStatus());
        
        if (!statusesCompatible(internalStatus, gatewayStatus)) {
            return createDiscrepancyRecord(run, gatewayRecord, payment,
                DiscrepancyType.STATUS_MISMATCH,
                String.format("Status mismatch: internal=%s, gateway=%s", 
                    internalStatus, gatewayStatus));
        }

        // Rule A: Exact match
        return createMatchedRecord(run, gatewayRecord, payment);
    }

    /**
     * Creates matched reconciliation record.
     */
    private ReconciliationRecord createMatchedRecord(
            ReconciliationRun run,
            GatewayPaymentRecord gatewayRecord,
            Payment payment) {

        return ReconciliationRecord.builder()
            .reconciliationRun(run)
            .merchantId(payment.getMerchant().getId())
            .gateway(gatewayRecord.getGateway())
            .gatewayReference(gatewayRecord.getGatewayReference())
            .paymentId(payment.getPaymentId())
            .internalAmount(payment.getAmount())
            .gatewayAmount(gatewayRecord.getAmount())
            .currency(payment.getCurrency())
            .internalStatus(payment.getStatus().name())
            .gatewayStatus(gatewayRecord.getStatus())
            .result(ReconciliationResult.MATCHED)
            .discrepancyType(DiscrepancyType.NONE)
            .reconciledAt(LocalDateTime.now())
            .notes("Exact match")
            .build();
    }

    /**
     * Creates discrepancy reconciliation record.
     */
    private ReconciliationRecord createDiscrepancyRecord(
            ReconciliationRun run,
            GatewayPaymentRecord gatewayRecord,
            Payment payment,
            DiscrepancyType discrepancyType,
            String notes) {

        return ReconciliationRecord.builder()
            .reconciliationRun(run)
            .merchantId(payment != null ? payment.getMerchant().getId() : run.getMerchantId())
            .gateway(gatewayRecord.getGateway())
            .gatewayReference(gatewayRecord.getGatewayReference())
            .paymentId(payment != null ? payment.getPaymentId() : null)
            .internalAmount(payment != null ? payment.getAmount() : null)
            .gatewayAmount(gatewayRecord.getAmount())
            .currency(gatewayRecord.getCurrency())
            .internalStatus(payment != null ? payment.getStatus().name() : null)
            .gatewayStatus(gatewayRecord.getStatus())
            .result(ReconciliationResult.DISCREPANCY)
            .discrepancyType(discrepancyType)
            .reconciledAt(LocalDateTime.now())
            .notes(notes)
            .build();
    }

    /**
     * Normalizes gateway status for comparison.
     */
    private String normalizeGatewayStatus(String gatewayStatus) {
        if (gatewayStatus == null) return "UNKNOWN";
        return gatewayStatus.toUpperCase();
    }

    /**
     * Checks if statuses are compatible.
     */
    private boolean statusesCompatible(String internalStatus, String gatewayStatus) {
        // Simple matching - can be enhanced
        if (internalStatus.equals(gatewayStatus)) return true;
        
        // Map common variations
        if ("CAPTURED".equals(internalStatus) && "CAPTURED".equals(gatewayStatus)) return true;
        if ("AUTHORIZED".equals(internalStatus) && "AUTHORIZED".equals(gatewayStatus)) return true;
        if ("FAILED".equals(internalStatus) && "FAILED".equals(gatewayStatus)) return true;
        
        return false;
    }

    /**
     * Retrieves reconciliation run by ID.
     */
    @Transactional(readOnly = true)
    public ReconciliationRun getRunById(Long runId) {
        return runRepository.findById(runId)
            .orElseThrow(() -> new BusinessException("RECON_RUN_NOT_FOUND", 
                "Reconciliation run not found: " + runId));
    }

    /**
     * Retrieves all reconciliation records for a run.
     */
    @Transactional(readOnly = true)
    public List<ReconciliationRecord> getRecordsByRunId(Long runId) {
        return recordRepository.findByReconciliationRunId(runId);
    }

    /**
     * Retrieves reconciliation runs for a merchant.
     */
    @Transactional(readOnly = true)
    public List<ReconciliationRun> getRunsByMerchantId(Long merchantId) {
        return runRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /**
     * Retrieves discrepancies for a merchant.
     */
    @Transactional(readOnly = true)
    public List<ReconciliationRecord> getDiscrepancies(Long merchantId) {
        return recordRepository.findByMerchantIdAndResult(merchantId, ReconciliationResult.DISCREPANCY);
    }
}
