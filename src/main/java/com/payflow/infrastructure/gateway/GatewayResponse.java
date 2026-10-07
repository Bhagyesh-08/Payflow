package com.payflow.infrastructure.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayResponse {
    private boolean success;
    private String gatewayPaymentId;
    private String gatewayStatus;
    private String message;
    private String errorCode;
}
