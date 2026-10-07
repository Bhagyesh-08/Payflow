package com.payflow.ledger.repository;

import com.payflow.ledger.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {
    
    Optional<LedgerTransaction> findByTransactionReference(String transactionReference);
    
    Optional<LedgerTransaction> findByPaymentId(String paymentId);
    
    List<LedgerTransaction> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
    
    boolean existsByTransactionReference(String transactionReference);
}
