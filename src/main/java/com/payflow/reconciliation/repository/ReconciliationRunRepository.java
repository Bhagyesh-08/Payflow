package com.payflow.reconciliation.repository;

import com.payflow.reconciliation.entity.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {
    
    List<ReconciliationRun> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
    
    Optional<ReconciliationRun> findByMerchantIdAndGatewayAndBusinessDate(
        Long merchantId, String gateway, LocalDate businessDate);
}
