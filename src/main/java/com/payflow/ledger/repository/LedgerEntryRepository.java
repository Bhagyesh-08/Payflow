package com.payflow.ledger.repository;

import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.enums.EntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    
    List<LedgerEntry> findByTransactionId(Long transactionId);
    
    List<LedgerEntry> findByAccountId(Long accountId);
    
    @Query("SELECT SUM(e.amount) FROM LedgerEntry e WHERE e.transaction.id = :transactionId AND e.entryType = :entryType")
    BigDecimal sumAmountByTransactionIdAndEntryType(
        @Param("transactionId") Long transactionId, 
        @Param("entryType") EntryType entryType);
}
