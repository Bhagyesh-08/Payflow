package com.payflow.dispute.dto;

import com.payflow.dispute.enums.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResponse {
    private String disputeId;
    private String paymentId;
    private Long merchantId;
    private BigDecimal amount;
    private String currency;
    private DisputeStatus status;
    private String gateway;
    private String gatewayDisputeId;
    private String reason;
    private LocalDateTime evidenceDueAt;
    private LocalDateTime resolvedAt;
    private String resolutionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
