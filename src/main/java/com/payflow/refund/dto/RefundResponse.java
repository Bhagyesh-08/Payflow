package com.payflow.refund.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundResponse {
    private Long id;
    private String refundId;
    private String paymentId;
    private Long merchantId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String gateway;
    private String gatewayRefundId;
    private String reason;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
