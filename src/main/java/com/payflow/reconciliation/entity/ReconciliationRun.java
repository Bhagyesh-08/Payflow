package com.payflow.reconciliation.entity;

import com.payflow.common.entity.BaseEntity;
import com.payflow.reconciliation.enums.ReconciliationRunStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reconciliation Run Entity.
 * 
 * Represents one reconciliation execution for a specific merchant/gateway/date.
 * Provides audit trail of reconciliation operations.
 */
@Entity
@Table(name = "reconciliation_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ReconciliationRun extends BaseEntity {

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 50)
    private String gateway;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationRunStatus status;

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "matched_records")
    private Integer matchedRecords;

    @Column(name = "discrepancy_records")
    private Integer discrepancyRecords;

    @Column(name = "unresolved_records")
    private Integer unresolvedRecords;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
