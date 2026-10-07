package com.payflow.reconciliation.entity;

import com.payflow.common.entity.BaseEntity;
import com.payflow.reconciliation.enums.DiscrepancyType;
import com.payflow.reconciliation.enums.ReconciliationResult;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reconciliation Record Entity.
 * 
 * Records the result of comparing one gateway payment record against internal data.
 * IMMUTABLE after creation - provides audit trail.
 */
@Entity
@Table(name = "reconciliation_records", uniqueConstraints = {
    @UniqueConstraint(name = "uk_recon_gateway_ref", columnNames = {"reconciliation_run_id", "gateway", "gateway_reference"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ReconciliationRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_run_id", nullable = false)
    private ReconciliationRun reconciliationRun;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 50)
    private String gateway;

    @Column(name = "gateway_reference", nullable = false, length = 200)
    private String gatewayReference;

    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "internal_amount", precision = 19, scale = 2)
    private BigDecimal internalAmount;

    @Column(name = "gateway_amount", precision = 19, scale = 2)
    private BigDecimal gatewayAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "internal_status", length = 30)
    private String internalStatus;

    @Column(name = "gateway_status", length = 30)
    private String gatewayStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, length = 50)
    private DiscrepancyType discrepancyType;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(length = 1000)
    private String notes;
}
