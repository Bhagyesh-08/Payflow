package com.payflow.reconciliation.enums;

/**
 * Reconciliation Result.
 * 
 * Indicates the outcome of comparing internal payment data with gateway data.
 */
public enum ReconciliationResult {
    /**
     * Gateway and internal records match
     */
    MATCHED,
    
    /**
     * Discrepancy detected between gateway and internal records
     */
    DISCREPANCY,
    
    /**
     * Unable to determine match/mismatch (e.g., gateway unavailable)
     */
    UNRESOLVED
}
