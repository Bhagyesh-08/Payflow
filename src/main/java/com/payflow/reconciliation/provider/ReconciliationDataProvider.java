package com.payflow.reconciliation.provider;

import com.payflow.reconciliation.model.GatewayPaymentRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * Reconciliation Data Provider.
 * 
 * Gateway-facing abstraction for retrieving reconciliation data.
 * Each gateway implementation provides its specific data retrieval logic.
 */
public interface ReconciliationDataProvider {
    
    /**
     * Retrieves gateway payment records for reconciliation.
     * 
     * @param merchantId Merchant identifier
     * @param businessDate Business date to reconcile
     * @return List of normalized gateway payment records
     * @throws ReconciliationDataException if data retrieval fails
     */
    List<GatewayPaymentRecord> getPaymentRecords(Long merchantId, LocalDate businessDate);
    
    /**
     * Returns gateway name.
     */
    String getGatewayName();
    
    /**
     * Checks if provider is available/configured.
     */
    boolean isAvailable();
}
