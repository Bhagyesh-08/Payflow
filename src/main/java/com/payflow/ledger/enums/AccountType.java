package com.payflow.ledger.enums;

/**
 * Ledger Account Types.
 * 
 * Represents the category of accounts used in double-entry bookkeeping.
 */
public enum AccountType {
    /**
     * Customer payment account (tracks money received from customers)
     */
    CUSTOMER_PAYMENT,
    
    /**
     * Merchant receivable account (tracks money owed to merchants)
     */
    MERCHANT_RECEIVABLE,
    
    /**
     * Platform fee account (for future fee collection)
     */
    PLATFORM_FEE,
    
    /**
     * Gateway clearing account (for future reconciliation)
     */
    GATEWAY_CLEARING,
    
    /**
     * Refund payable account (customer refunds)
     */
    CUSTOMER_REFUND,
    
    /**
     * Dispute loss account (chargebacks and lost disputes)
     */
    DISPUTE_LOSS
}
