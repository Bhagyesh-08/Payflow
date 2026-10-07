package com.payflow.reconciliation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Normalized Gateway Payment Record.
 * 
 * Gateway-agnostic representation of payment data from any gateway.
 * Reconciliation engine operates on this normalized model.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayPaymentRecord {
    
    /**
     * Gateway name (RAZORPAY, STRIPE, etc.)
     */
    private String gateway;
    
    /**
     * Gateway's payment/order/transaction identifier
     */
    private String gatewayReference;
    
    /**
     * Merchant reference if available (our internal payment ID)
     */
    private String merchantReference;
    
    /**
     * Payment amount
     */
    private BigDecimal amount;
    
    /**
     * Currency code (ISO 4217)
     */
    private String currency;
    
    /**
     * Gateway status (CAPTURED, FAILED, etc.)
     */
    private String status;
    
    /**
     * When payment was captured/completed at gateway
     */
    private LocalDateTime capturedAt;
    
    /**
     * When record was created at gateway
     */
    private LocalDateTime createdAt;
    
    /**
     * Additional metadata/notes
     */
    private String metadata;
}
