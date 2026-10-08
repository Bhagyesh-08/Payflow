package com.payflow.reconciliation.repository;

import com.payflow.reconciliation.entity.ReconciliationRecord;
import com.payflow.reconciliation.enums.DiscrepancyType;
import com.payflow.reconciliation.enums.ReconciliationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {
    
    List<ReconciliationRecord> findByReconciliationRunId(Long runId);
    
    List<ReconciliationRecord> findByMerchantIdAndResult(Long merchantId, ReconciliationResult result);
    
    Page<ReconciliationRecord> findByMerchantIdAndResult(Long merchantId, ReconciliationResult result, Pageable pageable);
    
    List<ReconciliationRecord> findByMerchantIdAndDiscrepancyType(Long merchantId, DiscrepancyType discrepancyType);
    
    Optional<ReconciliationRecord> findByPaymentId(String paymentId);
    
    int countByReconciliationRunIdAndResult(Long runId, ReconciliationResult result);
}
