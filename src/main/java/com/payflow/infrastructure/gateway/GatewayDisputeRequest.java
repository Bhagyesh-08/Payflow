package com.payflow.infrastructure.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gateway Dispute Request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayDisputeRequest {
    private String disputeId;
    private String evidenceDescription;
}
