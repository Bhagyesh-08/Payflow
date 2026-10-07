package com.payflow.infrastructure.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayPaymentRequest {
    private String internalPaymentId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String customerEmail;
}
