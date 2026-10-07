package com.payflow.payment.dto;

import com.payflow.payment.enums.PaymentStatus;
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
public class PaymentResponse {
    private Long id;
    private String paymentId;
    private Long orderId;
    private Long merchantId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String gatewayPaymentId;
    private String paymentMethod;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
