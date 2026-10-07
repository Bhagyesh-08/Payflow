package com.payflow.dispute.enums;

/**
 * Dispute Status Enum.
 * 
 * Represents the lifecycle of a dispute/chargeback.
 */
public enum DisputeStatus {
    /**
     * Dispute opened by customer/card network.
     */
    OPEN,
    
    /**
     * Evidence required from merchant.
     */
    EVIDENCE_REQUIRED,
    
    /**
     * Merchant has submitted evidence.
     */
    EVIDENCE_SUBMITTED,
    
    /**
     * Merchant won the dispute.
     */
    WON,
    
    /**
     * Merchant lost the dispute.
     */
    LOST,
    
    /**
     * Dispute closed (no further action).
     */
    CLOSED
}
