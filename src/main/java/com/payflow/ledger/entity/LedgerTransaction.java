package com.payflow.ledger.entity;

import com.payflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Ledger Transaction Entity.
 * 
 * Represents one atomic double-entry accounting transaction.
 * IMMUTABLE after creation - never updated or deleted.
 * 
 * Accounting Invariant: Total Debits = Total Credits
 */
@Entity
@Table(name = "ledger_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LedgerTransaction extends BaseEntity {

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 200)
    private String transactionReference;

    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 1000)
    private String description;
}
