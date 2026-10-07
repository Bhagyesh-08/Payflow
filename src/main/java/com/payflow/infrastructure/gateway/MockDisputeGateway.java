package com.payflow.infrastructure.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock Dispute Gateway.
 * 
 * Used when Razorpay SDK does not support dispute APIs.
 * Provides deterministic responses for testing.
 */
@Component
@Slf4j
public class MockDisputeGateway implements DisputeGateway {

    @Override
    public GatewayDisputeResponse submitEvidence(GatewayDisputeRequest request) {
        log.info("Mock: Submitting evidence for dispute: {}", request.getDisputeId());
        
        return GatewayDisputeResponse.builder()
            .success(true)
            .gatewayDisputeId("mock_disp_" + System.currentTimeMillis())
            .status("EVIDENCE_SUBMITTED")
            .message("Evidence submitted successfully (mock)")
            .build();
    }

    @Override
    public GatewayDisputeResponse getDisputeDetails(String gatewayDisputeId) {
        log.info("Mock: Fetching dispute details: {}", gatewayDisputeId);
        
        return GatewayDisputeResponse.builder()
            .success(true)
            .gatewayDisputeId(gatewayDisputeId)
            .status("OPEN")
            .message("Dispute details fetched (mock)")
            .build();
    }
}
