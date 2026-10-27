package com.payflow.infrastructure.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock Dispute Gateway.
 * 
 * Used because Razorpay SDK 1.4.6 does not support dispute management APIs.
 * Provides deterministic simulation responses for testing and development.
 */
@Component
@Slf4j
public class MockDisputeGateway implements DisputeGateway {

    @Override
    public GatewayDisputeResponse submitEvidence(GatewayDisputeRequest request) {
        log.warn("Mock Dispute Gateway: Simulating evidence submission for dispute: {} (MOCK_MODE)", request.getDisputeId());
        
        return GatewayDisputeResponse.builder()
            .success(true)
            .gatewayDisputeId("mock_disp_" + System.currentTimeMillis())
            .status("EVIDENCE_SUBMITTED")
            .message("Evidence submitted successfully (MOCK SIMULATION)")
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
