package com.payflow.ledger.repository;

import com.payflow.ledger.entity.LedgerAccount;
import com.payflow.ledger.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, Long> {
    
    Optional<LedgerAccount> findByMerchantIdAndAccountTypeAndCurrency(
        Long merchantId, AccountType accountType, String currency);
    
    Optional<LedgerAccount> findByAccountCode(String accountCode);
}
