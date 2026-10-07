package com.payflow.event.domain;

/**
 * Dispute Event Types.
 * 
 * Represents lifecycle events for disputes/chargebacks.
 */
public enum DisputeEventType {
    DISPUTE_CREATED,
    DISPUTE_EVIDENCE_REQUIRED,
    DISPUTE_EVIDENCE_SUBMITTED,
    DISPUTE_WON,
    DISPUTE_LOST,
    DISPUTE_CLOSED
}
