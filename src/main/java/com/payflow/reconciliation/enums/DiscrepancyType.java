package com.payflow.reconciliation.enums;

/**
 * Discrepancy Type.
 * 
 * Specific type of mismatch found during reconciliation.
 */
public enum DiscrepancyType {
    /**
     * No discrepancy (used for MATCHED results)
     */
    NONE,
    
    /**
     * Amount differs between gateway and internal
     */
    AMOUNT_MISMATCH,
    
    /**
     * Status differs between gateway and internal
     */
    STATUS_MISMATCH,
    
    /**
     * Gateway has record, internal system does not
     */
    MISSING_INTERNAL_PAYMENT,
    
    /**
     * Internal system has payment, gateway does not
     */
    MISSING_GATEWAY_PAYMENT,
    
    /**
     * Currency differs between gateway and internal
     */
    CURRENCY_MISMATCH,
    
    /**
     * Multiple gateway records found for same reference
     */
    DUPLICATE_GATEWAY_RECORD
}
