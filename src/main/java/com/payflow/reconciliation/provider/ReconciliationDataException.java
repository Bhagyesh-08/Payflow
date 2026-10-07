package com.payflow.reconciliation.provider;

/**
 * Reconciliation Data Exception.
 * 
 * Thrown when gateway data retrieval fails.
 */
public class ReconciliationDataException extends RuntimeException {
    
    public ReconciliationDataException(String message) {
        super(message);
    }
    
    public ReconciliationDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
