package com.payflow.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationRunResponse {
    private Long id;
    private Long merchantId;
    private String gateway;
    private LocalDate businessDate;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String status;
    private Integer totalRecords;
    private Integer matchedRecords;
    private Integer discrepancyRecords;
    private Integer unresolvedRecords;
    private String errorMessage;
}
