package com.payflow.infrastructure.gateway;

/**
 * Dispute Gateway Abstraction.
 * 
 * Decouples domain from gateway-specific implementations.
 */
public interface DisputeGateway {
    
    /**
     * Submits evidence for a dispute.
     * 
     * @param request the evidence submission request
     * @return gateway response
     */
    GatewayDisputeResponse submitEvidence(GatewayDisputeRequest request);
    
    /**
     * Fetches dispute details from gateway.
     * 
     * @param gatewayDisputeId the gateway dispute ID
     * @return dispute details
     */
    GatewayDisputeResponse getDisputeDetails(String gatewayDisputeId);
}
