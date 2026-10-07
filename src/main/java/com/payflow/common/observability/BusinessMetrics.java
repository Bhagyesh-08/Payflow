package com.payflow.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Business Metrics.
 * 
 * Centralizes Prometheus-compatible metrics for PayFlow.
 * Uses low-cardinality tags to avoid metric explosion.
 * 
 * CARDINALITY RULES:
 * - operation, status, eventType, gateway: OK (bounded values)
 * - paymentId, refundId, disputeId, correlationId: FORBIDDEN (unbounded)
 * 
 * All metrics prefixed with "payflow_" for easy discovery.
 */
@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    private final MeterRegistry registry;

    // ========================================
    // PAYMENT METRICS
    // ========================================

    public void incrementPaymentsCreated() {
        Counter.builder("payflow_payments_created_total")
                .description("Total payments created")
                .register(registry)
                .increment();
    }

    public void incrementPaymentsAuthorized() {
        Counter.builder("payflow_payments_authorized_total")
                .description("Total payments authorized")
                .register(registry)
                .increment();
    }

    public void incrementPaymentsCaptured() {
        Counter.builder("payflow_payments_captured_total")
                .description("Total payments captured")
                .register(registry)
                .increment();
    }

    public void incrementPaymentsFailed() {
        Counter.builder("payflow_payments_failed_total")
                .description("Total payments failed")
                .register(registry)
                .increment();
    }

    // ========================================
    // REFUND METRICS
    // ========================================

    public void incrementRefundsRequested() {
        Counter.builder("payflow_refunds_requested_total")
                .description("Total refunds requested")
                .register(registry)
                .increment();
    }

    public void incrementRefundsSucceeded() {
        Counter.builder("payflow_refunds_succeeded_total")
                .description("Total refunds succeeded")
                .register(registry)
                .increment();
    }

    public void incrementRefundsFailed() {
        Counter.builder("payflow_refunds_failed_total")
                .description("Total refunds failed")
                .register(registry)
                .increment();
    }

    public void recordRefundGatewayLatency(long durationMs) {
        Timer.builder("payflow_refund_gateway_latency")
                .description("Refund gateway execution latency")
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // ========================================
    // DISPUTE METRICS
    // ========================================

    public void incrementDisputesCreated() {
        Counter.builder("payflow_disputes_created_total")
                .description("Total disputes created")
                .register(registry)
                .increment();
    }

    public void incrementDisputesWon() {
        Counter.builder("payflow_disputes_won_total")
                .description("Total disputes won")
                .register(registry)
                .increment();
    }

    public void incrementDisputesLost() {
        Counter.builder("payflow_disputes_lost_total")
                .description("Total disputes lost")
                .register(registry)
                .increment();
    }

    // ========================================
    // WEBHOOK METRICS
    // ========================================

    public void incrementWebhooksReceived(String eventType) {
        Counter.builder("payflow_webhooks_received_total")
                .description("Total webhooks received")
                .tag("eventType", sanitizeEventType(eventType))
                .register(registry)
                .increment();
    }

    public void incrementWebhooksRejected(String reason) {
        Counter.builder("payflow_webhooks_rejected_total")
                .description("Total webhooks rejected")
                .tag("reason", sanitizeReason(reason))
                .register(registry)
                .increment();
    }

    public void incrementWebhooksDuplicate() {
        Counter.builder("payflow_webhooks_duplicate_total")
                .description("Total duplicate webhooks detected")
                .register(registry)
                .increment();
    }

    public void incrementWebhooksProcessingErrors() {
        Counter.builder("payflow_webhooks_processing_errors_total")
                .description("Total webhook processing errors")
                .register(registry)
                .increment();
    }

    public void recordWebhookProcessingLatency(long durationMs) {
        Timer.builder("payflow_webhook_processing_latency")
                .description("Webhook processing latency")
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // ========================================
    // KAFKA/OUTBOX METRICS
    // ========================================

    public void incrementOutboxEventsCreated(String eventType) {
        Counter.builder("payflow_outbox_events_created_total")
                .description("Total outbox events created")
                .tag("eventType", sanitizeEventType(eventType))
                .register(registry)
                .increment();
    }

    public void incrementOutboxEventsPublished(String eventType) {
        Counter.builder("payflow_outbox_events_published_total")
                .description("Total outbox events published to Kafka")
                .tag("eventType", sanitizeEventType(eventType))
                .register(registry)
                .increment();
    }

    public void incrementOutboxEventsFailed(String eventType) {
        Counter.builder("payflow_outbox_events_failed_total")
                .description("Total outbox events failed")
                .tag("eventType", sanitizeEventType(eventType))
                .register(registry)
                .increment();
    }

    public void incrementEventsConsumed(String eventType) {
        Counter.builder("payflow_events_consumed_total")
                .description("Total events consumed from Kafka")
                .tag("eventType", sanitizeEventType(eventType))
                .register(registry)
                .increment();
    }

    public void incrementDuplicateEvents(String consumerName) {
        Counter.builder("payflow_duplicate_events_total")
                .description("Total duplicate events detected by consumers")
                .tag("consumer", consumerName)
                .register(registry)
                .increment();
    }

    public void incrementEventProcessingErrors(String consumerName) {
        Counter.builder("payflow_event_processing_errors_total")
                .description("Total event processing errors")
                .tag("consumer", consumerName)
                .register(registry)
                .increment();
    }

    public void recordEventProcessingLatency(String consumerName, long durationMs) {
        Timer.builder("payflow_event_processing_latency")
                .description("Event processing latency")
                .tag("consumer", consumerName)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // ========================================
    // RECONCILIATION METRICS
    // ========================================

    public void incrementReconciliationRuns() {
        Counter.builder("payflow_reconciliation_runs_total")
                .description("Total reconciliation runs")
                .register(registry)
                .increment();
    }

    public void incrementReconciliationMatched(long count) {
        Counter.builder("payflow_reconciliation_matched_total")
                .description("Total matched reconciliation records")
                .register(registry)
                .increment(count);
    }

    public void incrementReconciliationDiscrepancies(long count) {
        Counter.builder("payflow_reconciliation_discrepancies_total")
                .description("Total reconciliation discrepancies detected")
                .register(registry)
                .increment(count);
    }

    public void recordReconciliationDuration(long durationMs) {
        Timer.builder("payflow_reconciliation_duration")
                .description("Reconciliation run duration")
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // ========================================
    // LEDGER METRICS
    // ========================================

    public void incrementLedgerTransactionsCreated() {
        Counter.builder("payflow_ledger_transactions_created_total")
                .description("Total ledger transactions created")
                .register(registry)
                .increment();
    }

    public void incrementLedgerTransactionsFailed() {
        Counter.builder("payflow_ledger_transactions_failed_total")
                .description("Total ledger transaction failures")
                .register(registry)
                .increment();
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Sanitizes event type for metric tag.
     * Keeps low cardinality by using only the root event type.
     */
    private String sanitizeEventType(String eventType) {
        if (eventType == null) {
            return "unknown";
        }
        // Extract first part: PAYMENT_CREATED -> PAYMENT
        String[] parts = eventType.split("_");
        return parts.length > 0 ? parts[0].toLowerCase() : "unknown";
    }

    /**
     * Sanitizes reason string to avoid unbounded cardinality.
     */
    private String sanitizeReason(String reason) {
        if (reason == null) {
            return "unknown";
        }
        // Map to known categories
        if (reason.contains("signature") || reason.contains("verification")) {
            return "signature_verification";
        }
        if (reason.contains("duplicate")) {
            return "duplicate";
        }
        if (reason.contains("parse") || reason.contains("format")) {
            return "parse_error";
        }
        return "other";
    }
}
