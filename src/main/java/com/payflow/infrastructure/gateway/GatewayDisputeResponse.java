package com.payflow.infrastructure.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gateway Dispute Response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayDisputeResponse {
    private boolean success;
    private String gatewayDisputeId;
    private String status;
    private String message;
}
