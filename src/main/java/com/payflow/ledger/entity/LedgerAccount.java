package com.payflow.ledger.entity;

import com.payflow.common.entity.BaseEntity;
import com.payflow.ledger.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Ledger Account Entity.
 * 
 * Represents an account participating in double-entry bookkeeping.
 * Accounts are long-lived and reusable across multiple transactions.
 */
@Entity
@Table(name = "ledger_accounts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_ledger_account", columnNames = {"merchant_id", "account_type", "currency"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LedgerAccount extends BaseEntity {

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "account_code", nullable = false, length = 50, unique = true)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String description;
}
